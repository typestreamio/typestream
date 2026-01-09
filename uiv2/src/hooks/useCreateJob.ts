import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useCreateJob() {
  return useMutation({
    ...JobService.methods.createJobFromGraph,
    service: JobService,
  });
}
