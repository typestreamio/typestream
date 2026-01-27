import { useMemo } from 'react';
import { useKafkaTopics } from './useKafkaTopics';
import { useConnections, type Connection } from './useConnections';

/**
 * Represents a Postgres table exposed via Debezium CDC.
 */
export interface PostgresTable {
  connectionId: string;
  connectionName: string;
  schema: string;
  table: string;
  topicPath: string;  // e.g., "/dev/kafka/local/topics/dbserver.public.orders"
  topicName: string;  // e.g., "dbserver.public.orders"
}

/**
 * Parse a Debezium topic name into its components.
 * Format: {prefix}.{schema}.{table}
 */
function parseDebeziumTopic(topicName: string): { prefix: string; schema: string; table: string } | null {
  const parts = topicName.split('.');
  if (parts.length !== 3) return null;
  return {
    prefix: parts[0],
    schema: parts[1],
    table: parts[2],
  };
}

/**
 * Get all Postgres tables available via Debezium CDC.
 * Correlates Debezium topics with registered connections.
 */
export function usePostgresTables() {
  const { debeziumTopics, isLoading: topicsLoading, error: topicsError } = useKafkaTopics();
  const { data: connections, isLoading: connectionsLoading, error: connectionsError } = useConnections();

  const tables = useMemo(() => {
    if (!debeziumTopics) return [];

    const result: PostgresTable[] = [];

    for (const topic of debeziumTopics) {
      const parsed = parseDebeziumTopic(topic.name);
      if (!parsed) continue;

      // For each topic, we create an entry - the connection will be selected in the node
      result.push({
        connectionId: '', // Will be set when user selects connection
        connectionName: '', // Will be set when user selects connection
        schema: parsed.schema,
        table: parsed.table,
        topicPath: `/dev/kafka/local/topics/${topic.name}`,
        topicName: topic.name,
      });
    }

    return result;
  }, [debeziumTopics]);

  // Get unique tables (deduplicated by topicName)
  const uniqueTables = useMemo(() => {
    const seen = new Set<string>();
    return tables.filter((t) => {
      if (seen.has(t.topicName)) return false;
      seen.add(t.topicName);
      return true;
    });
  }, [tables]);

  // Get connected Postgres connections for the source palette
  const postgresConnections = useMemo(() => {
    return (connections ?? []).filter(
      (c): c is Connection => c.databaseType === 'postgres' && c.state === 'connected'
    );
  }, [connections]);

  return {
    tables: uniqueTables,
    postgresConnections,
    isLoading: topicsLoading || connectionsLoading,
    error: topicsError || connectionsError,
  };
}
