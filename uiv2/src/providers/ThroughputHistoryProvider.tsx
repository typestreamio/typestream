import { useRef, useCallback, useState, type ReactNode } from 'react';
import type { JobInfo } from '../generated/job_pb';
import {
  ThroughputHistoryContext,
  MAX_HISTORY_POINTS,
} from './ThroughputHistoryContext';

interface ThroughputHistoryProviderProps {
  children: ReactNode;
}

/**
 * Provider that stores historical throughput data for sparkline visualization.
 * Maintains a rolling 2-minute window (120 points) of messagesPerSecond values per job.
 */
export function ThroughputHistoryProvider({ children }: ThroughputHistoryProviderProps) {
  // Use ref for the actual data to avoid copying on every read
  const historyRef = useRef<Map<string, number[]>>(new Map());
  // Version counter triggers re-renders when data changes
  const [version, setVersion] = useState(0);

  const getHistory = useCallback((jobId: string): number[] => {
    // Return a copy so React detects the change
    return [...(historyRef.current.get(jobId) ?? [])];
  }, []);

  // Track last recorded values to avoid unnecessary re-renders
  const lastValuesRef = useRef<Map<string, number>>(new Map());

  const recordValues = useCallback((jobs: JobInfo[]) => {
    const currentJobIds = new Set(jobs.map((j) => j.jobId));
    let hasChanges = false;

    // Record values for each job
    for (const job of jobs) {
      const value = job.throughput?.messagesPerSecond ?? 0;
      const lastValue = lastValuesRef.current.get(job.jobId);

      // Only record if value changed or this is a new job
      if (lastValue === undefined || lastValue !== value) {
        hasChanges = true;
        lastValuesRef.current.set(job.jobId, value);

        const history = historyRef.current.get(job.jobId) ?? [];

        // Add new value
        history.push(value);

        // Keep only the last MAX_HISTORY_POINTS
        if (history.length > MAX_HISTORY_POINTS) {
          history.shift();
        }

        historyRef.current.set(job.jobId, history);
      }
    }

    // Clean up jobs that no longer exist
    for (const jobId of historyRef.current.keys()) {
      if (!currentJobIds.has(jobId)) {
        historyRef.current.delete(jobId);
        lastValuesRef.current.delete(jobId);
        hasChanges = true;
      }
    }

    // Only increment version if data actually changed
    if (hasChanges) {
      setVersion((v) => v + 1);
    }
  }, []);

  return (
    <ThroughputHistoryContext.Provider value={{ getHistory, recordValues, version }}>
      {children}
    </ThroughputHistoryContext.Provider>
  );
}
