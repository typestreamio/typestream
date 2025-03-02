import { useKafkaTopics } from '../services/kafka';

export function KafkaTopics() {
  const { data, isLoading, error } = useKafkaTopics();

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
