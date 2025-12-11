import { useQuery } from '@connectrpc/connect-query';
import { listJobs } from '../generated/job-JobService_connectquery';

export function useJobsList(userId: string = 'default') {
  return useQuery(listJobs, {
    userId,
  }, {
    // Refetch every 3 seconds to keep list updated
    refetchInterval: 3000,
  });
}
