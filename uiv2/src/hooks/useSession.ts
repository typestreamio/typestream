import { useMutation } from '@connectrpc/connect-query';
import { InteractiveSessionService } from '../generated/interactive_session_connect';

export function useSession() {
  return useMutation({
    ...InteractiveSessionService.methods.startSession,
    service: InteractiveSessionService,
  });
}
