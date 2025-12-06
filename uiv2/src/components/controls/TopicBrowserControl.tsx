import { useKafkaTopics } from '../../hooks/useKafkaTopics';

export function TopicBrowserControl({ value, onChange }: {
  value: string;
  onChange: (value: string) => void;
}) {
  const { data, isLoading } = useKafkaTopics();

  if (isLoading) {
    return <select disabled><option>Loading topics...</option></select>;
  }

  return (
    <select value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">Select a topic...</option>
      {data?.files?.map((file) => (
        <option key={file.path} value={file.path}>
          {file.name}
        </option>
      ))}
    </select>
  );
}
