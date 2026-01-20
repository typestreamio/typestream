import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createElement } from 'react';
import { ReactFlowProvider } from '@xyflow/react';
import { TransportProvider } from '@connectrpc/connect-query';
import type { Transport } from '@connectrpc/connect';
import { StreamInspectorPanel } from './StreamInspectorPanel';

// Track calls to startPreview and stopPreview
const mockStartPreview = vi.fn();
const mockStopPreview = vi.fn();
let mockIsStreaming = false;
let mockMessages: Array<{ id: string; key: string; value: string; timestamp: number }> = [];
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
  const mockTransport = {} as Transport;
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
    mockMessages = [{ id: 'msg-1', key: 'k1', value: 'v1', timestamp: 1000 }];

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
      { id: 'msg-1', key: 'key1', value: '{"data": "test1"}', timestamp: 1704067200000 },
      { id: 'msg-2', key: 'key2', value: '{"data": "test2"}', timestamp: 1704067201000 },
    ];
    renderPanel(true);

    expect(screen.getByText('key1')).toBeInTheDocument();
    expect(screen.getByText('key2')).toBeInTheDocument();
    // JSON values are syntax highlighted (split into spans), so check for parts
    expect(screen.getByText('"test1"')).toBeInTheDocument();
    expect(screen.getByText('"test2"')).toBeInTheDocument();
  });

  it('should display message count', () => {
    mockMessages = [
      { id: 'msg-1', key: 'k1', value: 'v1', timestamp: 1000 },
      { id: 'msg-2', key: 'k2', value: 'v2', timestamp: 2000 },
    ];
    renderPanel(true);

    expect(screen.getByText('2 messages shown')).toBeInTheDocument();
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
    mockMessages = [{ id: 'msg-1', key: '', value: 'value-only', timestamp: 1000 }];
    renderPanel(true);

    expect(screen.getByText('-')).toBeInTheDocument();
    expect(screen.getByText('value-only')).toBeInTheDocument();
  });

  it('should expand message row when clicked', async () => {
    const user = userEvent.setup();
    mockMessages = [
      { id: 'msg-1', key: 'key1', value: '{"name": "test", "count": 42}', timestamp: 1704067200000 },
    ];
    renderPanel(true);

    // Initially shows collapsed value with syntax highlighting
    expect(screen.getByText('"name"')).toBeInTheDocument();
    expect(screen.getByText('"test"')).toBeInTheDocument();

    // Click to expand
    const row = screen.getByTestId('message-row-msg-1');
    await user.click(row);

    // After expanding, the formatted JSON should be visible with proper indentation
    // The SyntaxHighlighter renders the formatted JSON
    expect(screen.getByText(/"name"/)).toBeInTheDocument();
  });

  it('should collapse expanded message row when clicked again', async () => {
    const user = userEvent.setup();
    mockMessages = [
      { id: 'msg-1', key: 'key1', value: '{"name": "test"}', timestamp: 1704067200000 },
    ];
    renderPanel(true);

    // Click to expand
    const row = screen.getByTestId('message-row-msg-1');
    await user.click(row);

    // Click again to collapse
    await user.click(row);

    // Should show collapsed value again with syntax highlighting
    expect(screen.getByText('"name"')).toBeInTheDocument();
    expect(screen.getByText('"test"')).toBeInTheDocument();
  });

  it('should show expand icon for collapsed rows and collapse icon for expanded rows', async () => {
    const user = userEvent.setup();
    mockMessages = [
      { id: 'msg-1', key: 'key1', value: '{"data": "test"}', timestamp: 1704067200000 },
    ];
    renderPanel(true);

    // Initially should show expand button
    expect(screen.getByRole('button', { name: /expand/i })).toBeInTheDocument();

    // Click to expand
    const row = screen.getByTestId('message-row-msg-1');
    await user.click(row);

    // Should now show collapse button
    expect(screen.getByRole('button', { name: /collapse/i })).toBeInTheDocument();
  });

  it('should handle non-JSON values when expanded', async () => {
    const user = userEvent.setup();
    mockMessages = [
      { id: 'msg-1', key: 'key1', value: 'plain text message', timestamp: 1704067200000 },
    ];
    renderPanel(true);

    // Click to expand
    const row = screen.getByTestId('message-row-msg-1');
    await user.click(row);

    // Should still display the plain text
    expect(screen.getByText('plain text message')).toBeInTheDocument();
  });
});
