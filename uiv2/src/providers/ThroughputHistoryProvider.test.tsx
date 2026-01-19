import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { createElement } from 'react';
import {
  ThroughputHistoryProvider,
  useThroughputHistoryContext,
} from './ThroughputHistoryProvider';
import { useThroughputHistory } from '../hooks/useThroughputHistory';
import type { JobInfo } from '../generated/job_pb';

// Helper to create mock JobInfo
function createMockJob(jobId: string, messagesPerSecond: number): JobInfo {
  return {
    jobId,
    throughput: { messagesPerSecond, bytesPerSecond: 0 },
  } as JobInfo;
}

describe('ThroughputHistoryProvider', () => {
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(ThroughputHistoryProvider, null, children);

  describe('useThroughputHistoryContext', () => {
    it('should throw error when used outside provider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        renderHook(() => useThroughputHistoryContext());
      }).toThrow('useThroughputHistoryContext must be used within a ThroughputHistoryProvider');

      consoleSpy.mockRestore();
    });

    it('should provide context when inside provider', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      expect(result.current).toBeDefined();
      expect(result.current.getHistory).toBeInstanceOf(Function);
      expect(result.current.recordValues).toBeInstanceOf(Function);
      expect(result.current.version).toBe(0);
    });
  });

  describe('recordValues', () => {
    it('should record throughput values for jobs', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([
          createMockJob('job-1', 100),
          createMockJob('job-2', 200),
        ]);
      });

      expect(result.current.getHistory('job-1')).toEqual([100]);
      expect(result.current.getHistory('job-2')).toEqual([200]);
    });

    it('should accumulate history over multiple recordings', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([createMockJob('job-1', 100)]);
      });
      act(() => {
        result.current.recordValues([createMockJob('job-1', 150)]);
      });
      act(() => {
        result.current.recordValues([createMockJob('job-1', 200)]);
      });

      expect(result.current.getHistory('job-1')).toEqual([100, 150, 200]);
    });

    it('should maintain rolling window of max 120 points', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      // Record 130 values
      for (let i = 0; i < 130; i++) {
        act(() => {
          result.current.recordValues([createMockJob('job-1', i)]);
        });
      }

      const history = result.current.getHistory('job-1');
      expect(history).toHaveLength(120);
      // Should have values 10-129 (dropped first 10)
      expect(history[0]).toBe(10);
      expect(history[119]).toBe(129);
    });

    it('should increment version on data changes', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      expect(result.current.version).toBe(0);

      act(() => {
        result.current.recordValues([createMockJob('job-1', 100)]);
      });

      expect(result.current.version).toBe(1);

      act(() => {
        result.current.recordValues([createMockJob('job-1', 200)]);
      });

      expect(result.current.version).toBe(2);
    });

    it('should NOT increment version when values unchanged', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([createMockJob('job-1', 100)]);
      });

      expect(result.current.version).toBe(1);

      // Record same value
      act(() => {
        result.current.recordValues([createMockJob('job-1', 100)]);
      });

      // Version should NOT increment because value didn't change
      expect(result.current.version).toBe(1);
      // History should still only have one entry
      expect(result.current.getHistory('job-1')).toEqual([100]);
    });

    it('should only record changed values in multi-job updates', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([
          createMockJob('job-1', 100),
          createMockJob('job-2', 200),
        ]);
      });

      expect(result.current.version).toBe(1);

      // Only job-1 changes
      act(() => {
        result.current.recordValues([
          createMockJob('job-1', 150),
          createMockJob('job-2', 200), // unchanged
        ]);
      });

      expect(result.current.version).toBe(2);
      expect(result.current.getHistory('job-1')).toEqual([100, 150]);
      expect(result.current.getHistory('job-2')).toEqual([200]); // Only one entry
    });

    it('should clean up deleted jobs', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([
          createMockJob('job-1', 100),
          createMockJob('job-2', 200),
        ]);
      });

      expect(result.current.getHistory('job-1')).toEqual([100]);
      expect(result.current.getHistory('job-2')).toEqual([200]);

      // Only job-1 in next update
      act(() => {
        result.current.recordValues([createMockJob('job-1', 150)]);
      });

      expect(result.current.getHistory('job-1')).toEqual([100, 150]);
      expect(result.current.getHistory('job-2')).toEqual([]); // Cleaned up
    });

    it('should handle jobs with zero throughput', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([createMockJob('job-1', 0)]);
      });

      expect(result.current.getHistory('job-1')).toEqual([0]);
    });

    it('should handle jobs with undefined throughput', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      const jobWithNoThroughput = { jobId: 'job-1' } as JobInfo;

      act(() => {
        result.current.recordValues([jobWithNoThroughput]);
      });

      expect(result.current.getHistory('job-1')).toEqual([0]);
    });
  });

  describe('getHistory', () => {
    it('should return empty array for unknown job', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      expect(result.current.getHistory('unknown-job')).toEqual([]);
    });

    it('should return a copy of the history array', () => {
      const { result } = renderHook(() => useThroughputHistoryContext(), { wrapper });

      act(() => {
        result.current.recordValues([createMockJob('job-1', 100)]);
      });

      const history1 = result.current.getHistory('job-1');
      const history2 = result.current.getHistory('job-1');

      // Should be equal but not the same reference
      expect(history1).toEqual(history2);
      expect(history1).not.toBe(history2);
    });
  });
});

describe('useThroughputHistory', () => {
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(ThroughputHistoryProvider, null, children);

  it('should return empty array initially', () => {
    const { result } = renderHook(() => useThroughputHistory('job-1'), { wrapper });

    expect(result.current).toEqual([]);
  });

  it('should return history for specific job', () => {
    // Use a combined hook to ensure shared provider state
    let recordValuesFn: ((jobs: JobInfo[]) => void) | null = null;

    const CombinedHook = () => {
      const context = useThroughputHistoryContext();
      const history = useThroughputHistory('job-1');
      recordValuesFn = context.recordValues;
      return history;
    };

    const { result, rerender } = renderHook(() => CombinedHook(), { wrapper });

    expect(result.current).toEqual([]);

    act(() => {
      recordValuesFn!([createMockJob('job-1', 100)]);
    });

    rerender();

    expect(result.current).toEqual([100]);
  });

  it('should update when version changes', () => {
    // Using a combined wrapper and hook approach
    let recordValuesFn: ((jobs: JobInfo[]) => void) | null = null;

    const CombinedHook = () => {
      const context = useThroughputHistoryContext();
      const history = useThroughputHistory('job-1');
      recordValuesFn = context.recordValues;
      return { history, version: context.version };
    };

    const { result, rerender } = renderHook(() => CombinedHook(), { wrapper });

    expect(result.current.history).toEqual([]);

    act(() => {
      recordValuesFn!([createMockJob('job-1', 100)]);
    });

    rerender();

    expect(result.current.history).toEqual([100]);

    act(() => {
      recordValuesFn!([createMockJob('job-1', 200)]);
    });

    rerender();

    expect(result.current.history).toEqual([100, 200]);
  });
});
