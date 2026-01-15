import { createConnectQueryKey, useQuery } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export const listJobsQueryKey = (userId: string = 'local') =>
  createConnectQueryKey(
    { ...JobService.methods.listJobs, service: JobService },
    { userId }
  );

export function useListJobs(userId: string = 'local') {
  return useQuery(
    { ...JobService.methods.listJobs, service: JobService },
    { userId },
    { refetchInterval: 2000 }
  );
}
