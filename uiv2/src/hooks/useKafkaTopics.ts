import { useQuery } from '@connectrpc/connect-query';
import { FileSystemService } from '../generated/filesystem_connect';

export function useKafkaTopics(userId: string = 'local') {
  const query = useQuery(
    { ...FileSystemService.methods.ls, service: FileSystemService },
    { userId, path: '/dev/kafka/local/topics' }
  );

  const topics = query.data?.files ?? [];

  return {
    ...query,
    topics,
  };
}
