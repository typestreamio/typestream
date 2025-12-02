import { useMutation } from '@connectrpc/connect-query';
import { InteractiveSessionService } from '../generated/interactive_session_connect';

export function useRunProgram() {
  const methodDescriptor = {
    ...InteractiveSessionService.methods.runProgram,
    service: InteractiveSessionService,
  };

  return useMutation(methodDescriptor);
}
