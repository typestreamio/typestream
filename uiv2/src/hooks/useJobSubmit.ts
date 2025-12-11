import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useJobSubmit() {
  const methodDescriptor = {
    ...JobService.methods.createJob,
    service: JobService,
  };

  return useMutation(methodDescriptor);
}
