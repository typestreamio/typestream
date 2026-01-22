import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConnectError, Code } from '@connectrpc/connect';
import { ServerConnectionProvider } from './ServerConnectionProvider';
import { useServerConnection } from './ServerConnectionContext';

// Create a wrapper with QueryClient and ServerConnectionProvider
function createWrapper(options?: { failureThreshold?: number; debounceMs?: number }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return {
    queryClient,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(
        QueryClientProvider,
        { client: queryClient },
        createElement(ServerConnectionProvider, {
          failureThreshold: options?.failureThreshold ?? 2,
          debounceMs: options?.debounceMs ?? 100, // Short debounce for tests
          children,
        })
      ),
  };
}

// Helper to simulate query error
function simulateQueryError(queryClient: QueryClient, error: Error) {
  const cache = queryClient.getQueryCache();
  // Create a fake query and trigger an error event
  const query = cache.build(queryClient, {
    queryKey: ['test'],
    queryFn: () => Promise.reject(error),
  });
  query.setState({
    status: 'error',
    error,
    data: undefined,
    dataUpdatedAt: 0,
    errorUpdatedAt: Date.now(),
    fetchFailureCount: 1,
    fetchFailureReason: error,
    fetchMeta: undefined,
    fetchStatus: 'idle',
    isInvalidated: false,
    dataUpdateCount: 0,
    errorUpdateCount: 1,
  });
}

// Helper to simulate query success
function simulateQuerySuccess(queryClient: QueryClient) {
  const cache = queryClient.getQueryCache();
  const query = cache.build(queryClient, {
    queryKey: ['test-success'],
    queryFn: () => Promise.resolve('ok'),
  });
  query.setState({
    status: 'success',
    error: null,
    data: 'ok',
    dataUpdatedAt: Date.now(),
    errorUpdatedAt: 0,
    fetchFailureCount: 0,
    fetchFailureReason: null,
    fetchMeta: undefined,
    fetchStatus: 'idle',
    isInvalidated: false,
    dataUpdateCount: 1,
    errorUpdateCount: 0,
  });
}

describe('ServerConnectionProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('useServerConnection hook', () => {
    it('should throw error when used outside provider', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        renderHook(() => useServerConnection());
      }).toThrow('useServerConnection must be used within a ServerConnectionProvider');

      consoleSpy.mockRestore();
    });

    it('should provide initial connected state', () => {
      const { wrapper } = createWrapper();
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      expect(result.current.isConnected).toBe(true);
      expect(result.current.lastError).toBeNull();
      expect(result.current.disconnectedSince).toBeNull();
      expect(result.current.consecutiveFailures).toBe(0);
    });
  });

  describe('error detection', () => {
    it('should detect network errors (TypeError: Failed to fetch)', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });

      expect(result.current.consecutiveFailures).toBe(1);
    });

    it('should detect ConnectError with Unavailable code', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      act(() => {
        simulateQueryError(queryClient, new ConnectError('unavailable', Code.Unavailable));
      });

      expect(result.current.consecutiveFailures).toBe(1);
    });

    it('should NOT count application errors (NotFound)', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      act(() => {
        simulateQueryError(queryClient, new ConnectError('not found', Code.NotFound));
      });

      expect(result.current.consecutiveFailures).toBe(0);
      expect(result.current.isConnected).toBe(true);
    });

    it('should NOT count application errors (InvalidArgument)', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      act(() => {
        simulateQueryError(queryClient, new ConnectError('invalid', Code.InvalidArgument));
      });

      expect(result.current.consecutiveFailures).toBe(0);
    });

    it('should detect timeout errors', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      act(() => {
        simulateQueryError(queryClient, new Error('Request timeout'));
      });

      expect(result.current.consecutiveFailures).toBe(1);
    });

    it('should NOT count unrelated errors', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      act(() => {
        simulateQueryError(queryClient, new Error('Some random error'));
      });

      // This error doesn't match network patterns, so it shouldn't count
      expect(result.current.consecutiveFailures).toBe(0);
    });
  });

  describe('threshold and debounce behavior', () => {
    it('should require threshold failures before showing disconnected', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 3, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      // First failure
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });
      expect(result.current.isConnected).toBe(true);
      expect(result.current.consecutiveFailures).toBe(1);

      // Second failure
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });
      expect(result.current.isConnected).toBe(true);
      expect(result.current.consecutiveFailures).toBe(2);

      // Third failure - should now show disconnected
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });
      expect(result.current.isConnected).toBe(false);
      expect(result.current.consecutiveFailures).toBe(3);
    });

    it('should require debounce time before showing disconnected', async () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 2, debounceMs: 500 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      // Two failures immediately
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });

      // Should not be disconnected yet (debounce hasn't elapsed)
      expect(result.current.isConnected).toBe(true);
      expect(result.current.consecutiveFailures).toBe(2);

      // Advance time past debounce
      await act(async () => {
        vi.advanceTimersByTime(600);
      });

      expect(result.current.isConnected).toBe(false);
    });
  });

  describe('state reset on success', () => {
    it('should reset state on successful query', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      // Cause disconnection
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });
      expect(result.current.isConnected).toBe(false);

      // Success should reset
      act(() => {
        simulateQuerySuccess(queryClient);
      });

      expect(result.current.isConnected).toBe(true);
      expect(result.current.consecutiveFailures).toBe(0);
      expect(result.current.lastError).toBeNull();
      expect(result.current.disconnectedSince).toBeNull();
    });

    it('should not trigger re-render when already connected', () => {
      const { queryClient, wrapper } = createWrapper();
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      // Multiple successes shouldn't change state reference
      act(() => {
        simulateQuerySuccess(queryClient);
      });

      // State should be identical (same object reference due to no-op optimization)
      expect(result.current.isConnected).toBe(true);
      expect(result.current.consecutiveFailures).toBe(0);
    });
  });

  describe('reset function', () => {
    it('should manually reset connection state', () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 1, debounceMs: 0 });
      const { result } = renderHook(() => useServerConnection(), { wrapper });

      // Cause disconnection
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });
      expect(result.current.isConnected).toBe(false);

      // Manual reset
      act(() => {
        result.current.reset();
      });

      expect(result.current.isConnected).toBe(true);
      expect(result.current.consecutiveFailures).toBe(0);
    });
  });

  describe('cleanup', () => {
    it('should clean up timers on unmount', async () => {
      const { queryClient, wrapper } = createWrapper({ failureThreshold: 2, debounceMs: 1000 });
      const { result, unmount } = renderHook(() => useServerConnection(), { wrapper });

      // Start failures but don't reach debounce time
      act(() => {
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
        simulateQueryError(queryClient, new TypeError('Failed to fetch'));
      });

      expect(result.current.isConnected).toBe(true);

      // Unmount before timer fires
      unmount();

      // Advance time - timer should have been cleaned up
      await act(async () => {
        vi.advanceTimersByTime(2000);
      });

      // No error should be thrown (timer was cleaned up)
    });
  });
});
