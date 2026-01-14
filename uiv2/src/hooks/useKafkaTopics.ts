import { useQuery } from '@connectrpc/connect-query';
import { FileSystemService } from '../generated/filesystem_connect';
import type { FileInfo } from '../generated/filesystem_pb';

// Internal Kafka/Kafka Streams topics to hide from the UI
const INTERNAL_TOPIC_PATTERNS = [
  /-changelog$/,      // Kafka Streams state store changelogs
  /-repartition$/,    // Kafka Streams repartition topics
  /-count-store-/,    // TypeStream count state stores
  /-reduce-store-/,   // TypeStream reduce state stores
  /^__/,              // Kafka internal topics (__consumer_offsets, etc.)
  /^_schemas$/,       // Schema Registry topic
];

function isInternalTopic(name: string): boolean {
  return INTERNAL_TOPIC_PATTERNS.some((pattern) => pattern.test(name));
}

export function useKafkaTopics(userId: string = 'local') {
  const query = useQuery(
    { ...FileSystemService.methods.ls, service: FileSystemService },
    { userId, path: '/dev/kafka/local/topics' }
  );

  const allTopics: FileInfo[] = query.data?.files ?? [];
  const topics = allTopics.filter((topic) => !isInternalTopic(topic.name));

  return {
    ...query,
    topics,
  };
}
