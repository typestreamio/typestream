import { createGrpcWebTransport } from '@connectrpc/connect-web';

export const transport = createGrpcWebTransport({
  baseUrl: 'http://localhost:8080',
});
