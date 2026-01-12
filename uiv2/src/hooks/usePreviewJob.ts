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
      console.log('[usePreviewJob] startPreview called', { inspectorNodeId });
      setError(null);
      setMessages([]);

      const client = createClient(JobService, transport);

      try {
        // Create the preview job
        console.log('[usePreviewJob] Creating preview job...');
        const createResponse = await client.createPreviewJob(
          new CreatePreviewJobRequest({
            graph,
            inspectorNodeId,
          })
        );

        console.log('[usePreviewJob] createPreviewJob response:', {
          success: createResponse.success,
          jobId: createResponse.jobId,
          error: createResponse.error,
          inspectTopic: createResponse.inspectTopic,
        });

        if (!createResponse.success) {
          setError(createResponse.error || 'Failed to create preview job');
          return;
        }

        setJobId(createResponse.jobId);
        setIsStreaming(true);

        // Start streaming preview data
        abortControllerRef.current = new AbortController();

        console.log('[usePreviewJob] Starting stream for jobId:', createResponse.jobId);

        try {
          let messageCount = 0;
          for await (const response of client.streamPreview(
            new StreamPreviewRequest({ jobId: createResponse.jobId }),
            { signal: abortControllerRef.current.signal }
          )) {
            messageCount++;
            console.log('[usePreviewJob] Received message #', messageCount, {
              key: response.key,
              value: response.value?.substring(0, 100),
              timestamp: response.timestamp,
            });
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
          console.log('[usePreviewJob] Stream ended normally, total messages:', messageCount);
        } catch (streamError) {
          // AbortError is expected when stopping
          if ((streamError as Error).name !== 'AbortError') {
            console.error('[usePreviewJob] Stream error:', streamError);
            setError(`Stream error: ${(streamError as Error).message}`);
          } else {
            console.log('[usePreviewJob] Stream aborted (expected)');
          }
        }
      } catch (e) {
        console.error('[usePreviewJob] Error:', e);
        setError((e as Error).message || 'Failed to start preview');
      } finally {
        console.log('[usePreviewJob] startPreview finished');
        setIsStreaming(false);
      }
    },
    [transport]
  );

  const stopPreview = useCallback(async () => {
    console.log('[usePreviewJob] stopPreview called, jobId:', jobId);

    // Abort the stream
    if (abortControllerRef.current) {
      console.log('[usePreviewJob] Aborting stream...');
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    // Stop the preview job on the server
    if (jobId) {
      const client = createClient(JobService, transport);
      try {
        console.log('[usePreviewJob] Stopping preview job on server...');
        await client.stopPreviewJob(new StopPreviewJobRequest({ jobId }));
        console.log('[usePreviewJob] Preview job stopped successfully');
      } catch (e) {
        console.error('[usePreviewJob] Failed to stop preview job:', e);
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
