import { GrpcWebImpl, FileSystemServiceClientImpl } from '../generated/filesystem';

const transport = new GrpcWebImpl('http://localhost:8080', {
  debug: true,
});

export const fileSystemClient = new FileSystemServiceClientImpl(transport);
