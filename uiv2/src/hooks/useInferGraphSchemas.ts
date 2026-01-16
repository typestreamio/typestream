import { useMutation } from '@connectrpc/connect-query';
import { JobService } from '../generated/job_connect';

export function useInferGraphSchemas() {
  return useMutation({
    ...JobService.methods.inferGraphSchemas,
    service: JobService,
  });
}
