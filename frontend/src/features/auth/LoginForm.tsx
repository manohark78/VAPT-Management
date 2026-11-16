import React, { useState } from 'react';
import { request, ApiResponse } from '../../api/httpClient';

export interface UserScope {
  id: number;
  ipAddress: string | null;
  applicationName: string | null;
}

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: string;
  status: string;
  scopes: UserScope[];
}

interface LoginFormProps {
  onLoginSuccess: (user: AuthUser) => void;
}

export const LoginForm: React.FC<LoginFormProps> = ({ onLoginSuccess }) => {
  const [logoAvailable, setLogoAvailable] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const response = await request<ApiResponse<AuthUser>>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });

      if (response.success && response.data) {
        onLoginSuccess(response.data);
      } else {
        setError(response.error?.message || 'Login failed');
      }
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'error' in err) {
        const apiErr = (err as ApiResponse<unknown>).error;
        setError(apiErr?.message || 'Invalid credentials or server error');
      } else {
        setError('Network error or server unreachable');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'var(--color-bg)',
        padding: '1rem',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '420px',
          backgroundColor: 'var(--color-surface)',
          borderRadius: '8px',
          boxShadow: '0 4px 12px rgba(0, 0, 0, 0.08)',
          border: '1px solid var(--color-border)',
          overflow: 'hidden',
        }}
      >
        {/* Header Banner */}
        <div
          style={{
            backgroundColor: 'var(--color-primary)',
            padding: '1.5rem',
            textAlign: 'center',
            color: '#ffffff',
            borderBottom: '4px solid var(--color-accent)',
          }}
        >
          {logoAvailable && <img
            src="/assets/transunion-cibil-logo.svg"
            alt="TransUnion CIBIL"
            onError={() => setLogoAvailable(false)}
            style={{ display: 'block', width: 'min(195px, 70%)', height: 'auto', maxHeight: '56px', objectFit: 'contain', margin: '0 auto 1rem' }}
          />}
          <div
            style={{
              display: 'inline-block',
              backgroundColor: 'var(--color-accent)',
              color: 'var(--color-primary-dark)',
              fontWeight: 800,
              padding: '0.2rem 0.6rem',
              borderRadius: '4px',
              fontSize: '0.9rem',
              marginBottom: '0.5rem',
            }}
          >
            VAPT
          </div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 600 }}>VAPT Management Platform</h2>
          <p style={{ fontSize: '0.85rem', color: '#e2e8f0', marginTop: '0.25rem' }}>
            TransUnion CIBIL Information Security
          </p>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} style={{ padding: '2rem' }}>
          {error && (
            <div
              style={{
                backgroundColor: '#fee2e2',
                border: '1px solid #ef4444',
                color: '#b91c1c',
                padding: '0.75rem',
                borderRadius: '6px',
                fontSize: '0.85rem',
                marginBottom: '1.25rem',
              }}
            >
              {error}
            </div>
          )}

          <div style={{ marginBottom: '1.25rem' }}>
            <label
              htmlFor="email"
              style={{
                display: 'block',
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'var(--color-text-main)',
                marginBottom: '0.4rem',
              }}
            >
              Email Address
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@transunion.com"
              style={{
                width: '100%',
                padding: '0.65rem 0.75rem',
                borderRadius: '4px',
                border: '1px solid var(--color-border)',
                fontSize: '0.95rem',
                outline: 'none',
              }}
            />
          </div>

          <div style={{ marginBottom: '1.75rem' }}>
            <label
              htmlFor="password"
              style={{
                display: 'block',
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'var(--color-text-main)',
                marginBottom: '0.4rem',
              }}
            >
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              style={{
                width: '100%',
                padding: '0.65rem 0.75rem',
                borderRadius: '4px',
                border: '1px solid var(--color-border)',
                fontSize: '0.95rem',
                outline: 'none',
              }}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              backgroundColor: 'var(--color-primary)',
              color: '#ffffff',
              padding: '0.75rem',
              borderRadius: '4px',
              border: 'none',
              fontSize: '1rem',
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              opacity: loading ? 0.7 : 1,
              transition: 'background-color 0.2s',
            }}
          >
            {loading ? 'Authenticating...' : 'Sign In'}
          </button>

          <div
            style={{
              marginTop: '1.5rem',
              paddingTop: '1rem',
              borderTop: '1px solid var(--color-border)',
              fontSize: '0.75rem',
              color: 'var(--color-text-muted)',
              textAlign: 'center',
            }}
          >
            Authorized Personnel Only • Local Application Authentication
          </div>
        </form>
      </div>
    </div>
  );
};
