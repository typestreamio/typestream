import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { createElement } from 'react';
import { TransportProvider } from '@connectrpc/connect-query';
import { usePreviewJob } from './usePreviewJob';
import { PipelineGraph } from '../generated/job_pb';

// Mock the transport
const mockCreatePreviewJob = vi.fn();
const mockStopPreviewJob = vi.fn();
const mockStreamPreview = vi.fn();

vi.mock('@connectrpc/connect', () => ({
  createClient: () => ({
    createPreviewJob: mockCreatePreviewJob,
    stopPreviewJob: mockStopPreviewJob,
    streamPreview: mockStreamPreview,
  }),
}));

describe('usePreviewJob', () => {
  // Use unknown type to avoid explicit any, then cast when needed
  const mockTransport = {} as unknown;

  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(TransportProvider, { transport: mockTransport }, children);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should initialize with default state', () => {
    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    expect(result.current.jobId).toBeNull();
    expect(result.current.messages).toEqual([]);
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('should set error when createPreviewJob fails', async () => {
    mockCreatePreviewJob.mockResolvedValueOnce({
      success: false,
      error: 'Failed to create job',
      jobId: '',
      inspectTopic: '',
    });

    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    await act(async () => {
      await result.current.startPreview(new PipelineGraph(), 'node-1');
    });

    expect(result.current.error).toBe('Failed to create job');
    expect(result.current.isStreaming).toBe(false);
  });

  it('should set jobId and start streaming on success', async () => {
    mockCreatePreviewJob.mockResolvedValueOnce({
      success: true,
      error: '',
      jobId: 'job-123',
      inspectTopic: 'job-123-inspect-node-1',
    });

    // Mock async iterator for streaming
    const mockMessages = [
      { key: 'k1', value: 'v1', timestamp: BigInt(1000) },
      { key: 'k2', value: 'v2', timestamp: BigInt(2000) },
    ];

    mockStreamPreview.mockImplementationOnce(async function* () {
      for (const msg of mockMessages) {
        yield msg;
      }
    });

    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    await act(async () => {
      await result.current.startPreview(new PipelineGraph(), 'node-1');
    });

    expect(result.current.jobId).toBe('job-123');
    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0]).toEqual({
      key: 'k1',
      value: 'v1',
      timestamp: 1000,
    });
  });

  it('should limit messages to last 100', async () => {
    mockCreatePreviewJob.mockResolvedValueOnce({
      success: true,
      error: '',
      jobId: 'job-123',
      inspectTopic: 'job-123-inspect-node-1',
    });

    // Generate 150 messages
    const manyMessages = Array.from({ length: 150 }, (_, i) => ({
      key: `k${i}`,
      value: `v${i}`,
      timestamp: BigInt(i * 1000),
    }));

    mockStreamPreview.mockImplementationOnce(async function* () {
      for (const msg of manyMessages) {
        yield msg;
      }
    });

    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    await act(async () => {
      await result.current.startPreview(new PipelineGraph(), 'node-1');
    });

    expect(result.current.messages).toHaveLength(100);
    // Should have the last 100 messages (50-149)
    expect(result.current.messages[0].key).toBe('k50');
    expect(result.current.messages[99].key).toBe('k149');
  });

  it('should call stopPreviewJob when stopPreview is called', async () => {
    mockCreatePreviewJob.mockResolvedValueOnce({
      success: true,
      error: '',
      jobId: 'job-123',
      inspectTopic: 'job-123-inspect-node-1',
    });

    // Stream that never ends
    let resolveStream: () => void;
    const streamPromise = new Promise<void>((resolve) => {
      resolveStream = resolve;
    });

    // eslint-disable-next-line require-yield
    mockStreamPreview.mockImplementationOnce(async function* () {
      // Generator that waits indefinitely without yielding - simulates a hanging stream
      await streamPromise;
    });

    mockStopPreviewJob.mockResolvedValueOnce({ success: true, error: '' });

    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    // Start preview (don't await - it will hang on the stream)
    act(() => {
      result.current.startPreview(new PipelineGraph(), 'node-1');
    });

    // Wait for job to be created
    await waitFor(() => {
      expect(result.current.jobId).toBe('job-123');
    });

    // Now stop preview
    await act(async () => {
      await result.current.stopPreview();
    });

    expect(mockStopPreviewJob).toHaveBeenCalledWith(
      expect.objectContaining({ jobId: 'job-123' })
    );
    expect(result.current.jobId).toBeNull();
    expect(result.current.isStreaming).toBe(false);

    // Cleanup
    resolveStream!();
  });

  it('should handle stream abort gracefully', async () => {
    mockCreatePreviewJob.mockResolvedValueOnce({
      success: true,
      error: '',
      jobId: 'job-123',
      inspectTopic: 'job-123-inspect-node-1',
    });

    // Stream that throws AbortError
    // eslint-disable-next-line require-yield
    mockStreamPreview.mockImplementationOnce(async function* () {
      // Generator that immediately throws - simulates aborted stream
      const error = new Error('Aborted');
      error.name = 'AbortError';
      throw error;
    });

    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    await act(async () => {
      await result.current.startPreview(new PipelineGraph(), 'node-1');
    });

    // AbortError should not set error state
    expect(result.current.error).toBeNull();
    expect(result.current.isStreaming).toBe(false);
  });

  it('should clear messages when starting a new preview', async () => {
    mockCreatePreviewJob.mockResolvedValue({
      success: true,
      error: '',
      jobId: 'job-123',
      inspectTopic: 'job-123-inspect-node-1',
    });

    mockStreamPreview
      .mockImplementationOnce(async function* () {
        yield { key: 'old', value: 'data', timestamp: BigInt(1000) };
      })
      .mockImplementationOnce(async function* () {
        yield { key: 'new', value: 'data', timestamp: BigInt(2000) };
      });

    const { result } = renderHook(() => usePreviewJob(), { wrapper });

    // First preview
    await act(async () => {
      await result.current.startPreview(new PipelineGraph(), 'node-1');
    });

    expect(result.current.messages[0].key).toBe('old');

    // Second preview should clear old messages
    await act(async () => {
      await result.current.startPreview(new PipelineGraph(), 'node-2');
    });

    expect(result.current.messages).toHaveLength(1);
    expect(result.current.messages[0].key).toBe('new');
  });
});
