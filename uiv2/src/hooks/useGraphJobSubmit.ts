import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useGraphJobSubmit() {
  const methodDescriptor = {
    ...JobService.methods.createJobFromGraph,
    service: JobService,
  };

  return useMutation(methodDescriptor);
}
