import { useQuery } from '@connectrpc/connect-query';
import { FileSystemService } from '../generated/filesystem_connect';

export function useKafkaTopics(userId: string = 'local') {
  // Connect-query expects a method descriptor with service property
  const methodDescriptor = {
    ...FileSystemService.methods.ls,
    service: FileSystemService,
  };

  return useQuery(methodDescriptor, {
    userId,
    path: '/dev/kafka/local/topics',
  });
}
