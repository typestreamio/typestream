import { useQuery } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useListJobs(userId: string = 'local') {
  return useQuery(
    { ...JobService.methods.listJobs, service: JobService },
    { userId }
  );
}
