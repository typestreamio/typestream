import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useJobSubmit() {
  return useMutation({
    ...JobService.methods.createJob,
    service: JobService,
  });
}
