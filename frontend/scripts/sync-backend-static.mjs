import { cp, mkdir, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(frontendRoot, 'dist');
const backendStatic = path.resolve(frontendRoot, '..', 'backend', 'src', 'main', 'resources', 'static');

// Vite owns this generated index and asset directory in the Spring Boot static resource tree.
await mkdir(backendStatic, { recursive: true });
await rm(path.join(backendStatic, 'index.html'), { force: true });
await rm(path.join(backendStatic, 'assets'), { recursive: true, force: true });
await cp(path.join(dist, 'index.html'), path.join(backendStatic, 'index.html'));
await cp(path.join(dist, 'assets'), path.join(backendStatic, 'assets'), { recursive: true });
