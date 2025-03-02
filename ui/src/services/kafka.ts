import { useQuery } from '@tanstack/react-query';
import { fileSystemClient } from './grpc-client';

export const KAFKA_TOPICS_QUERY_KEY = ['kafka-topics'] as const;

export function useKafkaTopics() {
  return useQuery({
    queryKey: KAFKA_TOPICS_QUERY_KEY,
    queryFn: async () => {
      const response = await fileSystemClient.Ls({
        userId: 'local',
        path: '/dev/kafka/local/topics'
      });

      if (response.error) {
        throw new Error(response.error);
      }

      return response.files;
    }
  });
}
