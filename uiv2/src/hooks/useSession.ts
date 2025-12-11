import { useMutation } from '@connectrpc/connect-query';
import { InteractiveSessionService } from '../generated/interactive_session_connect';

export function useSession() {
  const methodDescriptor = {
    ...InteractiveSessionService.methods.startSession,
    service: InteractiveSessionService,
  };

  return useMutation(methodDescriptor);
}
