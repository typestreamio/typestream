import { useState, useCallback, useRef } from 'react';
import { useTransport } from '@connectrpc/connect-query';
import { createClient } from '@connectrpc/connect';
import { JobService } from '../generated/job_connect';
import {
  CreatePreviewJobRequest,
  StopPreviewJobRequest,
  StreamPreviewRequest,
  PipelineGraph,
} from '../generated/job_pb';

export interface PreviewMessage {
  key: string;
  value: string;
  timestamp: number;
}

export function usePreviewJob() {
  const [jobId, setJobId] = useState<string | null>(null);
  const [messages, setMessages] = useState<PreviewMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const transport = useTransport();

  const startPreview = useCallback(
    async (graph: PipelineGraph, inspectorNodeId: string) => {
      setError(null);
      setMessages([]);

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
          return;
        }

        setJobId(createResponse.jobId);
        setIsStreaming(true);

        // Start streaming preview data
        abortControllerRef.current = new AbortController();

        try {
          for await (const response of client.streamPreview(
            new StreamPreviewRequest({ jobId: createResponse.jobId }),
            { signal: abortControllerRef.current.signal }
          )) {
            setMessages((prev) => {
              const newMessages = [
                ...prev,
                {
                  key: response.key,
                  value: response.value,
                  timestamp: Number(response.timestamp),
                },
              ];
              // Keep only last 100 messages
              return newMessages.slice(-100);
            });
          }
        } catch (streamError) {
          // AbortError is expected when stopping
          if ((streamError as Error).name !== 'AbortError') {
            setError(`Stream error: ${(streamError as Error).message}`);
          }
        }
      } catch (e) {
        setError((e as Error).message || 'Failed to start preview');
      } finally {
        setIsStreaming(false);
      }
    },
    [transport]
  );

  const stopPreview = useCallback(async () => {
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
        // Silently handle stop errors - job may already be stopped
      }
      setJobId(null);
    }

    setIsStreaming(false);
  }, [jobId, transport]);

  return {
    jobId,
    messages,
    isStreaming,
    error,
    startPreview,
    stopPreview,
  };
}
