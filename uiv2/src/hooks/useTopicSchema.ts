import { useQuery } from '@connectrpc/connect-query';
import { FileSystemService } from '../generated/filesystem_connect';

export function useTopicSchema(topicPath: string, userId: string = 'local') {
  const query = useQuery(
    { ...FileSystemService.methods.getSchema, service: FileSystemService },
    { userId, path: topicPath },
    { enabled: !!topicPath }
  );

  const fields: string[] = query.data?.fields ?? [];

  return {
    ...query,
    fields,
  };
}
