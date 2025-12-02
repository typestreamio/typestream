import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useJobSubmit(userId: string = 'local') {
  const methodDescriptor = {
    ...JobService.methods.createJob,
    service: JobService,
  };

  return useMutation(methodDescriptor);
}
