import { useQuery } from '@connectrpc/connect-query';
import { FileSystemService } from '../generated/filesystem_connect';
import type { FileInfo } from '../generated/filesystem_pb';

export function useKafkaTopics(userId: string = 'local') {
  const query = useQuery(
    { ...FileSystemService.methods.ls, service: FileSystemService },
    { userId, path: '/dev/kafka/local/topics' }
  );

  const topics: FileInfo[] = query.data?.files ?? [];

  return {
    ...query,
    topics,
  };
}
