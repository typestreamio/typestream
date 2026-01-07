import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useGraphJobSubmit() {
  return useMutation({
    ...JobService.methods.createJobFromGraph,
    service: JobService,
  });
}
