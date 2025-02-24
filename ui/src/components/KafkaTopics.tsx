import { useEffect, useState } from 'react';
import { fileSystemClient } from '../services/grpc-client';

export function KafkaTopics() {
  const [topics, setTopics] = useState<string[]>([]);
  const [error, setError] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchTopics = async () => {
      try {
        const response = await fileSystemClient.Ls({
          userId: 'local',
          path: '/dev/kafka/local/topics'
        });

        if (response.error) {
          setError(response.error);
        } else {
          setTopics(response.files);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch topics');
      } finally {
        setLoading(false);
      }
    };

    fetchTopics();
  }, []);

  if (loading) {
    return <div>Loading topics...</div>;
  }

  if (error) {
    return <div>Error: {error}</div>;
  }

  if (topics.length === 0) {
    return <div>No topics found</div>;
  }

  return (
    <div>
      <h2>Kafka Topics</h2>
      <ul>
        {topics.map((topic) => (
          <li key={topic}>{topic}</li>
        ))}
      </ul>
    </div>
  );
}
