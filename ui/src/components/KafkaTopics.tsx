import { useQuery } from '@tanstack/react-query';
import { fileSystemClient } from '../services/grpc-client';

export function KafkaTopics() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['kafka-topics'],
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

  if (isLoading) {
    return <div>Loading topics...</div>;
  }

  if (error) {
    return <div>Error: {error instanceof Error ? error.message : 'Failed to fetch topics'}</div>;
  }

  if (!data || data.length === 0) {
    return <div>No topics found</div>;
  }

  return (
    <div>
      <h2>Kafka Topics</h2>
      <ul>
        {data.map((topic) => (
          <li key={topic}>{topic}</li>
        ))}
      </ul>
    </div>
  );
}
