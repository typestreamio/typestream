import { useState, useCallback, useRef, useEffect } from 'react';
import { useTransport } from '@connectrpc/connect-query';
import { createClient } from '@connectrpc/connect';
import { JobService } from '../generated/job_connect';
import {
  CreatePreviewJobRequest,
  StopPreviewJobRequest,
  StreamPreviewRequest,
  PipelineGraph,
} from '../generated/job_pb';
import type { PreviewMessage } from './usePreviewJob';

const MAX_SNAPSHOT_MESSAGES = 20;

export interface SnapshotPreviewState {
  messages: PreviewMessage[];
  currentIndex: number;
  currentMessage: PreviewMessage | null;
  isLoading: boolean;
  error: string | null;
  totalCount: number;
}

export interface SnapshotPreviewActions {
  startSnapshot: (graph: PipelineGraph, inspectorNodeId: string) => Promise<void>;
  stopSnapshot: () => Promise<void>;
  goToNext: () => void;
  goToPrevious: () => void;
  goToIndex: (index: number) => void;
}

export function useSnapshotPreview(): SnapshotPreviewState & SnapshotPreviewActions {
  const [messages, setMessages] = useState<PreviewMessage[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const transport = useTransport();

  const startSnapshot = useCallback(
    async (graph: PipelineGraph, inspectorNodeId: string) => {
      setError(null);
      setMessages([]);
      setCurrentIndex(0);
      setIsLoading(true);

      const client = createClient(JobService, transport);

      try {
        // Create the preview job
        const createResponse = await client.createPreviewJob(
          new CreatePreviewJobRequest({
            graph,
            inspectorNodeId,
          })
        );

        if (!createResponse.success) {
          setError(createResponse.error || 'Failed to create preview job');
          setIsLoading(false);
          return;
        }

        setJobId(createResponse.jobId);

        // Start streaming and capture up to MAX_SNAPSHOT_MESSAGES
        abortControllerRef.current = new AbortController();
        const collectedMessages: PreviewMessage[] = [];
        let messageCount = 0;

        try {
          for await (const response of client.streamPreview(
            new StreamPreviewRequest({ jobId: createResponse.jobId }),
            { signal: abortControllerRef.current.signal }
          )) {
            messageCount += 1;
            collectedMessages.push({
              id: `snapshot-msg-${messageCount}`,
              key: response.key,
              value: response.value,
              timestamp: Number(response.timestamp),
            });

            // Update messages as they arrive for responsive UI
            setMessages([...collectedMessages]);

            // Stop after collecting enough messages
            if (collectedMessages.length >= MAX_SNAPSHOT_MESSAGES) {
              abortControllerRef.current.abort();
              break;
            }
          }
        } catch (streamError) {
          // AbortError is expected when we stop after collecting enough messages
          if ((streamError as Error).name !== 'AbortError') {
            setError(`Stream error: ${(streamError as Error).message}`);
          }
        }

        // Stop the preview job on the server
        try {
          await client.stopPreviewJob(
            new StopPreviewJobRequest({ jobId: createResponse.jobId })
          );
        } catch {
          // Ignore stop errors - job may already be stopped
        }
      } catch (e) {
        setError((e as Error).message || 'Failed to start preview');
      } finally {
        setIsLoading(false);
        setJobId(null);
      }
    },
    [transport]
  );

  const stopSnapshot = useCallback(async () => {
    // Abort the stream
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    // Stop the preview job on the server
    if (jobId) {
      const client = createClient(JobService, transport);
      try {
        await client.stopPreviewJob(new StopPreviewJobRequest({ jobId }));
      } catch {
        // Ignore stop errors
      }
      setJobId(null);
    }

    setIsLoading(false);
  }, [jobId, transport]);

  const goToNext = useCallback(() => {
    setCurrentIndex((prev) => Math.min(prev + 1, messages.length - 1));
  }, [messages.length]);

  const goToPrevious = useCallback(() => {
    setCurrentIndex((prev) => Math.max(prev - 1, 0));
  }, []);

  const goToIndex = useCallback(
    (index: number) => {
      setCurrentIndex(Math.max(0, Math.min(index, messages.length - 1)));
    },
    [messages.length]
  );

  // Keep currentIndex in bounds when messages change
  useEffect(() => {
    if (messages.length > 0 && currentIndex >= messages.length) {
      setCurrentIndex(messages.length - 1);
    }
  }, [messages.length, currentIndex]);

  const currentMessage = messages.length > 0 ? messages[currentIndex] : null;

  return {
    messages,
    currentIndex,
    currentMessage,
    isLoading,
    error,
    totalCount: messages.length,
    startSnapshot,
    stopSnapshot,
    goToNext,
    goToPrevious,
    goToIndex,
  };
}
