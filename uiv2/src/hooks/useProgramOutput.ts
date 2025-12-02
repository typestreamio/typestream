import { useQuery } from '@connectrpc/connect-query';
import { InteractiveSessionService } from '../generated/interactive_session_connect';

export function useProgramOutput(sessionId: string, programId: string) {
  const methodDescriptor = {
    ...InteractiveSessionService.methods.getProgramOutput,
    service: InteractiveSessionService,
  };

  return useQuery(
    methodDescriptor,
    {
      sessionId,
      id: programId,
    },
    {
      enabled: !!sessionId && !!programId,
    }
  );
}
