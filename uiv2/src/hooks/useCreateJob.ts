import { useMutation } from '@connectrpc/connect-query';
import { useQueryClient } from '@tanstack/react-query';
import { JobService } from '../generated/job_connect';
import { listJobsQueryKey } from './useListJobs';

export function useCreateJob() {
  const queryClient = useQueryClient();

  return useMutation(
    {
      ...JobService.methods.createJobFromGraph,
      service: JobService,
    },
    {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: listJobsQueryKey() });
      },
    }
  );
}
