import { useQuery } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useListOpenAIModels() {
  return useQuery(
    { ...JobService.methods.listOpenAIModels, service: JobService },
    {}
  );
}
