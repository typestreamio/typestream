import { useKafkaTopics } from '../hooks/useKafkaTopics';

export function KafkaTopicBrowser({ userId = 'local' }: { userId?: string }) {
  const { data, isLoading, error } = useKafkaTopics(userId);

  if (isLoading) return <div>Loading topics...</div>;
  if (error) return <div>Error: {error.message}</div>;

  return (
    <div>
      <h2>Kafka Topics</h2>
      <ul>
        {data?.files?.map((file) => (
          <li key={file}>
            <strong>{file}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}
