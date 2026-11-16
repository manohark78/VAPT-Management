export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    correlationId: string;
    details?: string[];
  };
  timestamp: string;
  correlationId: string;
}

function getCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp('(^|;\\s*)XSRF-TOKEN=([^;]*)'));
  return match ? decodeURIComponent(match[2]) : null;
}

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const csrfToken = getCsrfToken();
  if (csrfToken && !['GET', 'HEAD', 'OPTIONS'].includes(options.method || 'GET')) {
    headers.set('X-XSRF-TOKEN', csrfToken);
  }

  // Ensure correlation ID is attached or let backend assign one
  if (!headers.has('X-Correlation-ID')) {
    headers.set('X-Correlation-ID', crypto.randomUUID());
  }

  const response = await fetch(url, { ...options, headers });
  
  if (!response.ok) {
    let errorJson;
    try {
      errorJson = await response.json();
    } catch {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    throw errorJson;
  }

  return response.json();
}

export function upload<T>(url: string, file: File, onProgress: (percent: number) => void, fields: Record<string, string> = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append('file', file);
    Object.entries(fields).forEach(([key, value]) => form.append(key, value));
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    xhr.withCredentials = true;
    xhr.setRequestHeader('X-Correlation-ID', crypto.randomUUID());
    const csrfToken = getCsrfToken();
    if (csrfToken) xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    xhr.upload.onload = () => onProgress(100);
    xhr.onerror = () => reject(new Error('Upload could not reach the server.'));
    xhr.onload = () => {
      let body: unknown;
      try {
        body = JSON.parse(xhr.responseText);
      } catch {
        reject(new Error(`Upload failed with HTTP ${xhr.status}.`));
        return;
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(body);
        return;
      }
      resolve(body as T);
    };
    xhr.send(form);
  });
}
