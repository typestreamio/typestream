import { GrpcWebImpl, FileSystemServiceClientImpl } from '../generated/filesystem';
import { InteractiveSessionServiceClientImpl } from '../generated/interactive_session';

const transport = new GrpcWebImpl('http://localhost:8080', {
  debug: true,
});

export const fileSystemClient = new FileSystemServiceClientImpl(transport);
export const interactiveSessionClient = new InteractiveSessionServiceClientImpl(transport);
