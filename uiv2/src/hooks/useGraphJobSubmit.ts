import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';
import type { CreateJobFromGraphRequest } from '../generated/job_pb';

export function useGraphJobSubmit(userId: string = 'local') {
  const methodDescriptor = {
    ...JobService.methods.createJobFromGraph,
    service: JobService,
  };

  return useMutation(methodDescriptor);
}
