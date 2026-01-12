import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createElement } from 'react';
import { ReactFlowProvider } from '@xyflow/react';
import { TransportProvider } from '@connectrpc/connect-query';
import { StreamInspectorPanel } from './StreamInspectorPanel';

// Track calls to startPreview and stopPreview
const mockStartPreview = vi.fn();
const mockStopPreview = vi.fn();
let mockIsStreaming = false;
let mockMessages: Array<{ key: string; value: string; timestamp: number }> = [];
let mockError: string | null = null;

// Mock the usePreviewJob hook
vi.mock('../hooks/usePreviewJob', () => ({
  usePreviewJob: () => ({
    messages: mockMessages,
    isStreaming: mockIsStreaming,
    error: mockError,
    startPreview: mockStartPreview,
    stopPreview: mockStopPreview,
  }),
}));

// Mock useReactFlow
vi.mock('@xyflow/react', async () => {
  const actual = await vi.importActual('@xyflow/react');
  return {
    ...actual,
    useReactFlow: () => ({
      getNodes: () => [],
      getEdges: () => [],
    }),
  };
});

describe('StreamInspectorPanel', () => {
  const mockTransport = {} as any;
  const mockOnClose = vi.fn();

  const renderPanel = (open = true) => {
    return render(
      createElement(
        TransportProvider,
        { transport: mockTransport },
        createElement(
          ReactFlowProvider,
          null,
          createElement(StreamInspectorPanel, {
            open,
            onClose: mockOnClose,
            nodeId: 'test-node-1',
          })
        )
      )
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockIsStreaming = false;
    mockMessages = [];
    mockError = null;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should call startPreview when panel opens', async () => {
    renderPanel(true);

    await waitFor(() => {
      expect(mockStartPreview).toHaveBeenCalledTimes(1);
    });

    expect(mockStartPreview).toHaveBeenCalledWith(
      expect.anything(), // PipelineGraph
      'test-node-1'
    );
  });

  it('should NOT call startPreview when panel is closed', () => {
    renderPanel(false);

    expect(mockStartPreview).not.toHaveBeenCalled();
  });

  it('should call stopPreview when close button is clicked', async () => {
    const user = userEvent.setup();
    renderPanel(true);

    // Wait for panel to open and start preview
    await waitFor(() => {
      expect(mockStartPreview).toHaveBeenCalled();
    });

    // Find and click the close button
    const closeButton = screen.getByRole('button', { name: /close/i });
    await user.click(closeButton);

    expect(mockStopPreview).toHaveBeenCalled();
    expect(mockOnClose).toHaveBeenCalled();
  });

  it('should NOT call stopPreview multiple times when hook updates', async () => {
    // This is the key test for the bug we fixed!
    // The bug was: when jobId changed in usePreviewJob, stopPreview was called
    // because it was in the dependency array of a cleanup effect

    const { rerender } = renderPanel(true);

    await waitFor(() => {
      expect(mockStartPreview).toHaveBeenCalledTimes(1);
    });

    // Simulate what happens when the preview job is created and state updates
    // This would cause stopPreview function reference to change
    mockIsStreaming = true;
    mockMessages = [{ key: 'k1', value: 'v1', timestamp: 1000 }];

    // Force a rerender (simulates hook state changing)
    rerender(
      createElement(
        TransportProvider,
        { transport: mockTransport },
        createElement(
          ReactFlowProvider,
          null,
          createElement(StreamInspectorPanel, {
            open: true,
            onClose: mockOnClose,
            nodeId: 'test-node-1',
          })
        )
      )
    );

    // stopPreview should NOT have been called yet
    expect(mockStopPreview).not.toHaveBeenCalled();

    // startPreview should only have been called once
    expect(mockStartPreview).toHaveBeenCalledTimes(1);
  });

  it('should call stopPreview on unmount', async () => {
    const { unmount } = renderPanel(true);

    await waitFor(() => {
      expect(mockStartPreview).toHaveBeenCalled();
    });

    unmount();

    expect(mockStopPreview).toHaveBeenCalled();
  });

  it('should display error message when error occurs', () => {
    mockError = 'Connection failed';
    renderPanel(true);

    expect(screen.getByText('Connection failed')).toBeInTheDocument();
  });

  it('should display messages in the table', () => {
    mockMessages = [
      { key: 'key1', value: '{"data": "test1"}', timestamp: 1704067200000 },
      { key: 'key2', value: '{"data": "test2"}', timestamp: 1704067201000 },
    ];
    renderPanel(true);

    expect(screen.getByText('key1')).toBeInTheDocument();
    expect(screen.getByText('key2')).toBeInTheDocument();
    expect(screen.getByText('{"data": "test1"}')).toBeInTheDocument();
    expect(screen.getByText('{"data": "test2"}')).toBeInTheDocument();
  });

  it('should display message count', () => {
    mockMessages = [
      { key: 'k1', value: 'v1', timestamp: 1000 },
      { key: 'k2', value: 'v2', timestamp: 2000 },
    ];
    renderPanel(true);

    expect(screen.getByText('2 messages (last 100 shown)')).toBeInTheDocument();
  });

  it('should show loading indicator when streaming', () => {
    mockIsStreaming = true;
    renderPanel(true);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('should show "No messages yet" when empty and not streaming', () => {
    mockIsStreaming = false;
    mockMessages = [];
    mockError = null;
    renderPanel(true);

    expect(screen.getByText('No messages yet')).toBeInTheDocument();
  });

  it('should handle dash key display for empty keys', () => {
    mockMessages = [{ key: '', value: 'value-only', timestamp: 1000 }];
    renderPanel(true);

    expect(screen.getByText('-')).toBeInTheDocument();
    expect(screen.getByText('value-only')).toBeInTheDocument();
  });
});
