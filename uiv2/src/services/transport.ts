import { createGrpcWebTransport } from '@connectrpc/connect-web';

const baseUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export const transport = createGrpcWebTransport({
  baseUrl,
});
