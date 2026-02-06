import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTransport } from '@connectrpc/connect-query';
import { createClient } from '@connectrpc/connect';
import { ConnectionService } from '../generated/connection_connect';
import {
  GetConnectionStatusesRequest,
  RegisterConnectionRequest,
  UnregisterConnectionRequest,
  TestConnectionRequest,
  DatabaseConnectionConfig,
  DatabaseType,
  GetWeaviateConnectionStatusesRequest,
  RegisterWeaviateConnectionRequest,
  WeaviateConnectionConfig,
  GetElasticsearchConnectionStatusesRequest,
  RegisterElasticsearchConnectionRequest,
  ElasticsearchConnectionConfig,
} from '../generated/connection_pb';

const CONNECTIONS_KEY = ['connections'];
const WEAVIATE_CONNECTIONS_KEY = ['weaviateConnections'];
const ELASTICSEARCH_CONNECTIONS_KEY = ['elasticsearchConnections'];

/**
 * Connection data structure used throughout the UI.
 * Note: Password is intentionally excluded for security - credentials stay server-side.
 */
export interface Connection {
  id: string;
  name: string;
  databaseType: 'postgres' | 'mysql';
  hostname: string;
  connectorHostname: string;  // Hostname for Kafka Connect (Docker network)
  port: number;
  database: string;
  username: string;
  // password intentionally excluded - credentials resolved server-side
  state: 'connected' | 'disconnected' | 'error' | 'connecting' | 'unknown';
  error?: string;
  lastChecked?: Date;
}

/**
 * Convert proto DatabaseType to string
 */
function databaseTypeToString(type: DatabaseType): 'postgres' | 'mysql' {
  switch (type) {
    case DatabaseType.POSTGRES:
      return 'postgres';
    case DatabaseType.MYSQL:
      return 'mysql';
    default:
      return 'postgres';
  }
}

/**
 * Convert string to proto DatabaseType
 */
function stringToDatabaseType(type: 'postgres' | 'mysql'): DatabaseType {
  return type === 'postgres' ? DatabaseType.POSTGRES : DatabaseType.MYSQL;
}

/**
 * Fetch all connections from the backend
 * Polls every 5 seconds for live status updates
 */
export function useConnections() {
  const transport = useTransport();

  return useQuery({
    queryKey: CONNECTIONS_KEY,
    queryFn: async (): Promise<Connection[]> => {
      const client = createClient(ConnectionService, transport);
      const response = await client.getConnectionStatuses(new GetConnectionStatusesRequest());

      return response.statuses.map((status) => ({
        id: status.id,
        name: status.name,
        databaseType: status.config ? databaseTypeToString(status.config.databaseType) : 'postgres',
        hostname: status.config?.hostname || '',
        connectorHostname: status.config?.connectorHostname || status.config?.hostname || '',
        port: status.config?.port || 5432,
        database: status.config?.database || '',
        username: status.config?.username || '',
        // password excluded - credentials resolved server-side
        state: mapConnectionState(status.state),
        error: status.error || undefined,
        lastChecked: status.lastChecked ? new Date(Number(status.lastChecked.seconds) * 1000) : undefined,
      }));
    },
    refetchInterval: 5000,
  });
}

/**
 * Map proto connection state to UI state
 */
function mapConnectionState(state: number): Connection['state'] {
  switch (state) {
    case 1: return 'connected';
    case 2: return 'disconnected';
    case 3: return 'error';
    case 4: return 'connecting';
    default: return 'unknown';
  }
}

/**
 * Register a new connection with the backend
 */
export function useRegisterConnection() {
  const transport = useTransport();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (config: {
      id: string;
      name: string;
      databaseType: 'postgres' | 'mysql';
      hostname: string;
      port: number;
      database: string;
      username: string;
      password: string;
    }) => {
      const client = createClient(ConnectionService, transport);
      const request = new RegisterConnectionRequest({
        connection: new DatabaseConnectionConfig({
          id: config.id,
          name: config.name,
          databaseType: stringToDatabaseType(config.databaseType),
          hostname: config.hostname,
          port: config.port,
          database: config.database,
          username: config.username,
          password: config.password,
        }),
      });
      return client.registerConnection(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONNECTIONS_KEY });
    },
  });
}

/**
 * Unregister a connection from the backend
 */
export function useUnregisterConnection() {
  const transport = useTransport();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (connectionId: string) => {
      const client = createClient(ConnectionService, transport);
      const request = new UnregisterConnectionRequest({ connectionId });
      return client.unregisterConnection(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONNECTIONS_KEY });
    },
  });
}

/**
 * Test a connection (one-shot, doesn't register)
 */
export function useTestConnection() {
  const transport = useTransport();

  return useMutation({
    mutationFn: async (config: {
      id: string;
      name: string;
      databaseType: 'postgres' | 'mysql';
      hostname: string;
      port: number;
      database: string;
      username: string;
      password: string;
    }) => {
      const client = createClient(ConnectionService, transport);
      const request = new TestConnectionRequest({
        connection: new DatabaseConnectionConfig({
          id: config.id,
          name: config.name,
          databaseType: stringToDatabaseType(config.databaseType),
          hostname: config.hostname,
          port: config.port,
          database: config.database,
          username: config.username,
          password: config.password,
        }),
      });
      return client.testConnection(request);
    },
  });
}

/**
 * Get connections that can be used as sinks (for NodePalette)
 * Only returns connected connections
 */
export function useSinkConnections() {
  const { data: connections, ...rest } = useConnections();

  return {
    ...rest,
    data: connections?.filter((c) => c.state === 'connected'),
  };
}

/**
 * Create a JDBC sink connector using a registered connection.
 * Credentials are resolved server-side for security.
 */
export function useCreateJdbcSinkConnector() {
  const transport = useTransport();

  return useMutation({
    mutationFn: async (config: {
      connectionId: string;
      connectorName: string;
      topics: string;
      tableName: string;
      insertMode: string;
      primaryKeyFields?: string;
    }) => {
      const client = createClient(ConnectionService, transport);
      const { CreateJdbcSinkConnectorRequest } = await import('../generated/connection_pb');
      const request = new CreateJdbcSinkConnectorRequest({
        connectionId: config.connectionId,
        connectorName: config.connectorName,
        topics: config.topics,
        tableName: config.tableName,
        insertMode: config.insertMode,
        primaryKeyFields: config.primaryKeyFields || '',
      });
      return client.createJdbcSinkConnector(request);
    },
  });
}

// ==================== Weaviate Connection Hooks ====================

/**
 * Weaviate connection data structure used throughout the UI.
 * Note: API key is intentionally excluded for security - credentials stay server-side.
 */
export interface WeaviateConnection {
  id: string;
  name: string;
  restUrl: string;
  grpcUrl: string;
  grpcSecured: boolean;
  authScheme: string;
  connectorRestUrl: string;
  connectorGrpcUrl: string;
  // api_key intentionally excluded - credentials resolved server-side
  state: 'connected' | 'disconnected' | 'error' | 'connecting' | 'unknown';
  error?: string;
  lastChecked?: Date;
}

/**
 * Fetch all Weaviate connections from the backend
 * Polls every 5 seconds for live status updates
 */
export function useWeaviateConnections() {
  const transport = useTransport();

  return useQuery({
    queryKey: WEAVIATE_CONNECTIONS_KEY,
    queryFn: async (): Promise<WeaviateConnection[]> => {
      const client = createClient(ConnectionService, transport);
      const response = await client.getWeaviateConnectionStatuses(new GetWeaviateConnectionStatusesRequest());

      return response.statuses.map((status) => ({
        id: status.id,
        name: status.name,
        restUrl: status.config?.restUrl || '',
        grpcUrl: status.config?.grpcUrl || '',
        grpcSecured: status.config?.grpcSecured || false,
        authScheme: status.config?.authScheme || 'NONE',
        connectorRestUrl: status.config?.connectorRestUrl || '',
        connectorGrpcUrl: status.config?.connectorGrpcUrl || '',
        // api_key excluded - credentials resolved server-side
        state: mapConnectionState(status.state),
        error: status.error || undefined,
        lastChecked: status.lastChecked ? new Date(Number(status.lastChecked.seconds) * 1000) : undefined,
      }));
    },
    refetchInterval: 5000,
  });
}

/**
 * Register a new Weaviate connection with the backend
 */
export function useRegisterWeaviateConnection() {
  const transport = useTransport();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (config: {
      id: string;
      name: string;
      restUrl: string;
      grpcUrl: string;
      grpcSecured?: boolean;
      authScheme?: string;
      apiKey?: string;
      connectorRestUrl?: string;
      connectorGrpcUrl?: string;
    }) => {
      const client = createClient(ConnectionService, transport);
      const request = new RegisterWeaviateConnectionRequest({
        connection: new WeaviateConnectionConfig({
          id: config.id,
          name: config.name,
          restUrl: config.restUrl,
          grpcUrl: config.grpcUrl,
          grpcSecured: config.grpcSecured || false,
          authScheme: config.authScheme || 'NONE',
          apiKey: config.apiKey || '',
          connectorRestUrl: config.connectorRestUrl || '',
          connectorGrpcUrl: config.connectorGrpcUrl || '',
        }),
      });
      return client.registerWeaviateConnection(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: WEAVIATE_CONNECTIONS_KEY });
    },
  });
}

/**
 * Get Weaviate connections that can be used as sinks (for NodePalette)
 * Only returns connected connections
 */
export function useWeaviateSinkConnections() {
  const { data: connections, ...rest } = useWeaviateConnections();

  return {
    ...rest,
    data: connections?.filter((c) => c.state === 'connected'),
  };
}

/**
 * Get all sink connections (both DB and Weaviate) for NodePalette
 */
export function useAllSinkConnections() {
  const { data: dbConnections } = useSinkConnections();
  const { data: weaviateConnections } = useWeaviateSinkConnections();
  const { data: elasticsearchConnections } = useElasticsearchSinkConnections();

  return {
    dbConnections: dbConnections ?? [],
    weaviateConnections: weaviateConnections ?? [],
    elasticsearchConnections: elasticsearchConnections ?? [],
  };
}

// ==================== Elasticsearch Connection Hooks ====================

/**
 * Elasticsearch connection data structure used throughout the UI.
 * Note: Password is intentionally excluded for security - credentials stay server-side.
 */
export interface ElasticsearchConnection {
  id: string;
  name: string;
  connectionUrl: string;
  username: string;
  connectorUrl: string;
  // password intentionally excluded - credentials resolved server-side
  state: 'connected' | 'disconnected' | 'error' | 'connecting' | 'unknown';
  error?: string;
  lastChecked?: Date;
}

/**
 * Fetch all Elasticsearch connections from the backend
 * Polls every 5 seconds for live status updates
 */
export function useElasticsearchConnections() {
  const transport = useTransport();

  return useQuery({
    queryKey: ELASTICSEARCH_CONNECTIONS_KEY,
    queryFn: async (): Promise<ElasticsearchConnection[]> => {
      const client = createClient(ConnectionService, transport);
      const response = await client.getElasticsearchConnectionStatuses(new GetElasticsearchConnectionStatusesRequest());

      return response.statuses.map((status) => ({
        id: status.id,
        name: status.name,
        connectionUrl: status.config?.connectionUrl || '',
        username: status.config?.username || '',
        connectorUrl: status.config?.connectorUrl || '',
        // password excluded - credentials resolved server-side
        state: mapConnectionState(status.state),
        error: status.error || undefined,
        lastChecked: status.lastChecked ? new Date(Number(status.lastChecked.seconds) * 1000) : undefined,
      }));
    },
    refetchInterval: 5000,
  });
}

/**
 * Register a new Elasticsearch connection with the backend
 */
export function useRegisterElasticsearchConnection() {
  const transport = useTransport();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (config: {
      id: string;
      name: string;
      connectionUrl: string;
      username?: string;
      password?: string;
      connectorUrl?: string;
    }) => {
      const client = createClient(ConnectionService, transport);
      const request = new RegisterElasticsearchConnectionRequest({
        connection: new ElasticsearchConnectionConfig({
          id: config.id,
          name: config.name,
          connectionUrl: config.connectionUrl,
          username: config.username || '',
          password: config.password || '',
          connectorUrl: config.connectorUrl || '',
        }),
      });
      return client.registerElasticsearchConnection(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ELASTICSEARCH_CONNECTIONS_KEY });
    },
  });
}

/**
 * Get Elasticsearch connections that can be used as sinks (for NodePalette)
 * Only returns connected connections
 */
export function useElasticsearchSinkConnections() {
  const { data: connections, ...rest } = useElasticsearchConnections();

  return {
    ...rest,
    data: connections?.filter((c) => c.state === 'connected'),
  };
}
