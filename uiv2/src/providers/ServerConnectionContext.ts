import { createContext, useContext } from 'react';

export interface ServerConnectionState {
  /** Whether the server is currently reachable */
  isConnected: boolean;
  /** The last connection error, if any */
  lastError: Error | null;
  /** Timestamp of when disconnection was first detected */
  disconnectedSince: Date | null;
  /** Number of consecutive failures */
  consecutiveFailures: number;
}

export interface ServerConnectionContextValue extends ServerConnectionState {
  /** Manually reset the connection state */
  reset: () => void;
}

export const ServerConnectionContext =
  createContext<ServerConnectionContextValue | null>(null);

/**
 * Hook to access server connection state.
 * Must be used within a ServerConnectionProvider.
 */
export function useServerConnection() {
  const context = useContext(ServerConnectionContext);
  if (!context) {
    throw new Error(
      'useServerConnection must be used within a ServerConnectionProvider'
    );
  }
  return context;
}
