/**
 * Kafka Connect REST API client
 * Routes through Envoy proxy at /connect/*
 */

const CONNECT_BASE_URL = '/connect';

export interface ConnectorStatus {
  name: string;
  connector: {
    state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'UNASSIGNED';
    worker_id: string;
  };
  tasks: Array<{
    id: number;
    state: 'RUNNING' | 'PAUSED' | 'FAILED' | 'UNASSIGNED';
    worker_id: string;
    trace?: string;
  }>;
  type: 'source' | 'sink';
}

export interface ConnectorInfo {
  name: string;
  config: Record<string, string>;
  tasks: Array<{ connector: string; task: number }>;
  type: 'source' | 'sink';
}

export interface ConnectorPlugin {
  class: string;
  type: string;
  version: string;
}

export interface PostgresSourceConfig {
  name: string;
  'connector.class': 'io.debezium.connector.postgresql.PostgresConnector';
  'database.hostname': string;
  'database.port': string;
  'database.user': string;
  'database.password': string;
  'database.dbname': string;
  'topic.prefix': string;
  'table.include.list'?: string;
  'schema.include.list'?: string;
  'plugin.name': 'pgoutput';
  'slot.name'?: string;
  'publication.name'?: string;
  'key.converter': string;
  'key.converter.schema.registry.url': string;
  'value.converter': string;
  'value.converter.schema.registry.url': string;
}

export type JDBCSinkInsertMode = 'insert' | 'upsert' | 'update';

export interface JDBCSinkConfig {
  name: string;
  'connector.class': 'io.debezium.connector.jdbc.JdbcSinkConnector';
  'connection.url': string;
  'connection.username': string;
  'connection.password': string;
  'topics': string;
  'table.name.format': string;
  'insert.mode': JDBCSinkInsertMode;
  'primary.key.mode'?: 'record_key' | 'record_value' | 'kafka' | 'none';
  'primary.key.fields'?: string;
  'key.converter': string;
  'key.converter.schema.registry.url': string;
  'value.converter': string;
  'value.converter.schema.registry.url': string;
  'schema.evolution'?: 'none' | 'basic';
}

export interface CreateConnectorRequest {
  name: string;
  config: Record<string, string>;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    let message = `HTTP ${response.status}`;
    try {
      const json = JSON.parse(text);
      message = json.message || json.error_code || message;
    } catch {
      if (text) message = text;
    }
    throw new Error(message);
  }
  return response.json();
}

export const connectApi = {
  /**
   * List all connector names
   */
  async listConnectors(): Promise<string[]> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors`);
    return handleResponse<string[]>(response);
  },

  /**
   * Get connector info
   */
  async getConnector(name: string): Promise<ConnectorInfo> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors/${encodeURIComponent(name)}`);
    return handleResponse<ConnectorInfo>(response);
  },

  /**
   * Get connector status
   */
  async getConnectorStatus(name: string): Promise<ConnectorStatus> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors/${encodeURIComponent(name)}/status`);
    return handleResponse<ConnectorStatus>(response);
  },

  /**
   * Create a new connector
   */
  async createConnector(request: CreateConnectorRequest): Promise<ConnectorInfo> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    return handleResponse<ConnectorInfo>(response);
  },

  /**
   * Delete a connector
   */
  async deleteConnector(name: string): Promise<void> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors/${encodeURIComponent(name)}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error(`Failed to delete connector: ${response.statusText}`);
    }
  },

  /**
   * Restart a connector
   */
  async restartConnector(name: string): Promise<void> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors/${encodeURIComponent(name)}/restart`, {
      method: 'POST',
    });
    if (!response.ok) {
      throw new Error(`Failed to restart connector: ${response.statusText}`);
    }
  },

  /**
   * Pause a connector
   */
  async pauseConnector(name: string): Promise<void> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors/${encodeURIComponent(name)}/pause`, {
      method: 'PUT',
    });
    if (!response.ok) {
      throw new Error(`Failed to pause connector: ${response.statusText}`);
    }
  },

  /**
   * Resume a connector
   */
  async resumeConnector(name: string): Promise<void> {
    const response = await fetch(`${CONNECT_BASE_URL}/connectors/${encodeURIComponent(name)}/resume`, {
      method: 'PUT',
    });
    if (!response.ok) {
      throw new Error(`Failed to resume connector: ${response.statusText}`);
    }
  },

  /**
   * List available connector plugins
   */
  async listPlugins(): Promise<ConnectorPlugin[]> {
    const response = await fetch(`${CONNECT_BASE_URL}/connector-plugins`);
    return handleResponse<ConnectorPlugin[]>(response);
  },

  /**
   * Build a Postgres source connector config
   */
  buildPostgresSourceConfig(params: {
    name: string;
    hostname: string;
    port: string;
    database: string;
    username: string;
    password: string;
    topicPrefix: string;
    tableFilter?: string;
    schemaFilter?: string;
  }): CreateConnectorRequest {
    const config: Record<string, string> = {
      'connector.class': 'io.debezium.connector.postgresql.PostgresConnector',
      'database.hostname': params.hostname,
      'database.port': params.port,
      'database.user': params.username,
      'database.password': params.password,
      'database.dbname': params.database,
      'topic.prefix': params.topicPrefix,
      'plugin.name': 'pgoutput',
      'slot.name': `${params.name.replace(/-/g, '_')}_slot`,
      'publication.name': `${params.name.replace(/-/g, '_')}_pub`,
      'key.converter': 'io.confluent.connect.avro.AvroConverter',
      'key.converter.schema.registry.url': 'http://redpanda:8081',
      'value.converter': 'io.confluent.connect.avro.AvroConverter',
      'value.converter.schema.registry.url': 'http://redpanda:8081',
    };

    if (params.tableFilter) {
      config['table.include.list'] = params.tableFilter;
    }
    if (params.schemaFilter) {
      config['schema.include.list'] = params.schemaFilter;
    }

    return { name: params.name, config };
  },

  /**
   * Build a JDBC sink connector config
   */
  buildJdbcSinkConfig(params: {
    name: string;
    databaseType: 'postgres' | 'mysql';
    hostname: string;
    port: string;
    database: string;
    username: string;
    password: string;
    topics: string;
    tableName: string;
    insertMode: 'insert' | 'upsert' | 'update';
    primaryKeyFields?: string;
  }): CreateConnectorRequest {
    // Build JDBC URL based on database type
    const jdbcUrl =
      params.databaseType === 'postgres'
        ? `jdbc:postgresql://${params.hostname}:${params.port}/${params.database}`
        : `jdbc:mysql://${params.hostname}:${params.port}/${params.database}`;

    const config: Record<string, string> = {
      'connector.class': 'io.debezium.connector.jdbc.JdbcSinkConnector',
      'connection.url': jdbcUrl,
      'connection.username': params.username,
      'connection.password': params.password,
      'topics': params.topics,
      'table.name.format': params.tableName,
      'insert.mode': params.insertMode,
      'key.converter': 'org.apache.kafka.connect.storage.StringConverter',
      'value.converter': 'io.confluent.connect.avro.AvroConverter',
      'value.converter.schema.registry.url': 'http://redpanda:8081',
      'schema.evolution': 'basic',
    };

    // For upsert/update mode, configure primary key handling
    if (params.insertMode === 'upsert' || params.insertMode === 'update') {
      if (params.primaryKeyFields) {
        config['primary.key.mode'] = 'record_value';
        config['primary.key.fields'] = params.primaryKeyFields;
      } else {
        // Default to using the record key
        config['primary.key.mode'] = 'record_key';
      }
    }

    return { name: params.name, config };
  },
};
