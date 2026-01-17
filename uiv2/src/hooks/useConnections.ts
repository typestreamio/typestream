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
} from '../generated/connection_pb';

const CONNECTIONS_KEY = ['connections'];

/**
 * Connection data structure used throughout the UI
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
  password: string;
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
        port: status.config?.port || '',
        database: status.config?.database || '',
        username: status.config?.username || '',
        password: status.config?.password || '',
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
      port: string;
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
      port: string;
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
