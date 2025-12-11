import { useKafkaTopics } from '../hooks/useKafkaTopics';

export function KafkaTopicBrowser({ userId = 'local' }: { userId?: string }) {
  const { data, isLoading, error } = useKafkaTopics(userId);

  if (isLoading) return <div>Loading topics...</div>;
  if (error) return <div>Error: {error.message}</div>;

  return (
    <div>
      <h2>Kafka Topics</h2>
      <ul>
        {data?.files?.map((file: any, index: number) => (
          <li key={typeof file === 'string' ? file : file.name || index}>
            {typeof file === 'string' ? (
              <strong>{file}</strong>
            ) : (
              <>
                <strong>{file.name}</strong> - {file.type === 'FILE' ? 'Topic' : 'Directory'}
                {file.schema && <pre>{JSON.stringify(file.schema, null, 2)}</pre>}
              </>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
