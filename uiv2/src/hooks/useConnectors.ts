import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { connectApi, type ConnectorStatus, type CreateConnectorRequest } from '../services/connectApi';

const CONNECTORS_KEY = ['connectors'];
const connectorKey = (name: string) => ['connector', name];
const connectorStatusKey = (name: string) => ['connector', name, 'status'];

export interface ConnectorWithStatus {
  name: string;
  status: ConnectorStatus | null;
  error?: string;
}

/**
 * Fetch all connectors with their statuses
 */
export function useConnectors() {
  return useQuery({
    queryKey: CONNECTORS_KEY,
    queryFn: async (): Promise<ConnectorWithStatus[]> => {
      const names = await connectApi.listConnectors();
      const results = await Promise.all(
        names.map(async (name) => {
          try {
            const status = await connectApi.getConnectorStatus(name);
            return { name, status };
          } catch (err) {
            return {
              name,
              status: null,
              error: err instanceof Error ? err.message : 'Unknown error'
            };
          }
        })
      );
      return results;
    },
    refetchInterval: 10000, // Refresh every 10 seconds
  });
}

/**
 * Fetch a single connector's status
 */
export function useConnectorStatus(name: string) {
  return useQuery({
    queryKey: connectorStatusKey(name),
    queryFn: () => connectApi.getConnectorStatus(name),
    enabled: !!name,
    refetchInterval: 5000,
  });
}

/**
 * Create a new connector
 */
export function useCreateConnector() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateConnectorRequest) => connectApi.createConnector(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONNECTORS_KEY });
    },
  });
}

/**
 * Delete a connector
 */
export function useDeleteConnector() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => connectApi.deleteConnector(name),
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: CONNECTORS_KEY });
      queryClient.removeQueries({ queryKey: connectorKey(name) });
    },
  });
}

/**
 * Restart a connector
 */
export function useRestartConnector() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => connectApi.restartConnector(name),
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: connectorStatusKey(name) });
    },
  });
}

/**
 * Pause a connector
 */
export function usePauseConnector() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => connectApi.pauseConnector(name),
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: connectorStatusKey(name) });
      queryClient.invalidateQueries({ queryKey: CONNECTORS_KEY });
    },
  });
}

/**
 * Resume a connector
 */
export function useResumeConnector() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => connectApi.resumeConnector(name),
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: connectorStatusKey(name) });
      queryClient.invalidateQueries({ queryKey: CONNECTORS_KEY });
    },
  });
}
