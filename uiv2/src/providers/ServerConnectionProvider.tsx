import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ConnectError, Code } from '@connectrpc/connect';
import {
  ServerConnectionContext,
  type ServerConnectionState,
} from './ServerConnectionContext';

interface ServerConnectionProviderProps {
  children: ReactNode;
  /** Number of consecutive failures before showing disconnected (default: 3) */
  failureThreshold?: number;
  /** Minimum time in ms failures must persist before showing disconnected (default: 3000) */
  debounceMs?: number;
}

/** Application error codes that are NOT connection problems */
const APPLICATION_ERROR_CODES = new Set([
  Code.InvalidArgument,
  Code.NotFound,
  Code.AlreadyExists,
  Code.PermissionDenied,
  Code.FailedPrecondition,
  Code.OutOfRange,
  Code.Unimplemented,
  Code.Internal,
  Code.DataLoss,
  Code.Unauthenticated,
]);

/**
 * Determines if an error indicates a connection/network problem
 * vs an application-level error.
 */
function isConnectionError(error: unknown): boolean {
  // ConnectError - check if it's an application error vs connection error
  if (error instanceof ConnectError) {
    // If it's a known application error code, it's NOT a connection problem
    if (APPLICATION_ERROR_CODES.has(error.code)) {
      return false;
    }
    // Otherwise (Unavailable, Unknown, DeadlineExceeded, ResourceExhausted, Aborted, Canceled)
    // treat as potential connection issue
    return true;
  }

  // Non-ConnectError = network/fetch failure = connection problem
  if (error instanceof Error) {
    return true;
  }

  return false;
}

export function ServerConnectionProvider({
  children,
  failureThreshold = 2,
  debounceMs = 1500,
}: ServerConnectionProviderProps) {
  const queryClient = useQueryClient();

  const [state, setState] = useState<ServerConnectionState>({
    isConnected: true,
    lastError: null,
    disconnectedSince: null,
    consecutiveFailures: 0,
  });

  // Track timing for debounce
  const firstFailureTimeRef = useRef<number | null>(null);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const handleSuccess = useCallback(() => {
    // Any success immediately resets the connection state
    firstFailureTimeRef.current = null;
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }

    setState({
      isConnected: true,
      lastError: null,
      disconnectedSince: null,
      consecutiveFailures: 0,
    });
  }, []);

  const handleError = useCallback(
    (error: unknown) => {
      if (!isConnectionError(error)) {
        // Application error, not a connection problem
        return;
      }

      const now = Date.now();

      setState((prev) => {
        const newFailures = prev.consecutiveFailures + 1;
        const errorObj =
          error instanceof Error ? error : new Error(String(error));

        // Track when failures started
        if (firstFailureTimeRef.current === null) {
          firstFailureTimeRef.current = now;
        }

        // Check if we've exceeded threshold AND time requirement
        const timeSinceFirstFailure = now - firstFailureTimeRef.current;
        const shouldShowDisconnected =
          newFailures >= failureThreshold && timeSinceFirstFailure >= debounceMs;

        if (shouldShowDisconnected && prev.isConnected) {
          // Transition to disconnected
          return {
            isConnected: false,
            lastError: errorObj,
            disconnectedSince: new Date(firstFailureTimeRef.current),
            consecutiveFailures: newFailures,
          };
        }

        // Not yet showing disconnected, but schedule a check
        if (
          !shouldShowDisconnected &&
          newFailures >= failureThreshold &&
          !debounceTimerRef.current
        ) {
          const remainingTime = debounceMs - timeSinceFirstFailure;
          debounceTimerRef.current = setTimeout(() => {
            debounceTimerRef.current = null;
            if (!mountedRef.current) return;
            setState((current) => {
              if (current.consecutiveFailures >= failureThreshold) {
                return {
                  ...current,
                  isConnected: false,
                  disconnectedSince: current.disconnectedSince || new Date(),
                };
              }
              return current;
            });
          }, remainingTime);
        }

        return {
          ...prev,
          lastError: errorObj,
          consecutiveFailures: newFailures,
        };
      });
    },
    [failureThreshold, debounceMs]
  );

  const reset = useCallback(() => {
    firstFailureTimeRef.current = null;
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    setState({
      isConnected: true,
      lastError: null,
      disconnectedSince: null,
      consecutiveFailures: 0,
    });
  }, []);

  // Subscribe to QueryCache events
  useEffect(() => {
    const queryCache = queryClient.getQueryCache();

    const unsubscribe = queryCache.subscribe((event) => {
      if (event.type === 'updated') {
        const query = event.query;

        // Check query state
        if (query.state.status === 'error') {
          handleError(query.state.error);
        } else if (query.state.status === 'success') {
          handleSuccess();
        }
      }
    });

    return () => {
      mountedRef.current = false;
      unsubscribe();
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [queryClient, handleError, handleSuccess]);

  return (
    <ServerConnectionContext.Provider value={{ ...state, reset }}>
      {children}
    </ServerConnectionContext.Provider>
  );
}
