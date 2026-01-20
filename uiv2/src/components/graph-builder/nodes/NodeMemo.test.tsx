import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { ReactFlowProvider, ReactFlow, useNodesState, type Node } from '@xyflow/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { MemoryRouter } from 'react-router-dom';
import { memo, useEffect, useRef } from 'react';
import { nodeTypes, type KafkaSourceNodeData, type KafkaSinkNodeData } from './index';

// Mock the hooks that make network calls
vi.mock('../../../hooks/useKafkaTopics', () => ({
  useKafkaTopics: () => ({
    topics: [
      { name: 'test-topic', encoding: 0 },
      { name: 'web_visits', encoding: 0 },
    ],
    isLoading: false,
    error: null,
  }),
}));

vi.mock('../../../hooks/useListOpenAIModels', () => ({
  useListOpenAIModels: () => ({
    data: { models: [{ id: 'gpt-4o-mini', name: 'GPT-4o Mini' }] },
    isLoading: false,
  }),
}));

// Providers wrapper
function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  const theme = createTheme();

  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <ReactFlowProvider>
            <div style={{ width: 800, height: 600 }}>
              {children}
            </div>
          </ReactFlowProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
}

describe('Node memo behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Memo verification with render tracking', () => {
    it('memoized component should have stable render count during position changes', async () => {
      // Track renders using a ref that persists across renders
      const renderCountRef = { current: {} as Record<string, number> };

      // Create a memoized node component that tracks renders
      const MemoizedTestNode = memo(function MemoizedTestNode({ id }: { id: string }) {
        // Track renders in an effect to avoid double-counting from strict mode
        const isFirstRender = useRef(true);
        useEffect(() => {
          if (isFirstRender.current) {
            renderCountRef.current[id] = 1;
            isFirstRender.current = false;
          } else {
            renderCountRef.current[id] = (renderCountRef.current[id] || 0) + 1;
          }
        });
        return <div data-testid={`node-${id}`}>Node {id}</div>;
      });

      // Define stable nodeTypes outside the component
      const testNodeTypes = { test: MemoizedTestNode };

      const setNodesRef = { current: null as ReturnType<typeof useNodesState>[1] | null };

      const initialNodes: Node[] = [
        { id: 'node-1', type: 'test', position: { x: 0, y: 0 }, data: {} },
        { id: 'node-2', type: 'test', position: { x: 200, y: 0 }, data: {} },
      ];

      function TestGraph() {
        const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
        setNodesRef.current = setNodes;

        return (
          <ReactFlow
            nodes={nodes}
            edges={[]}
            onNodesChange={onNodesChange}
            nodeTypes={testNodeTypes}
          />
        );
      }

      render(
        <TestWrapper>
          <TestGraph />
        </TestWrapper>
      );

      await screen.findByTestId('node-node-1');
      await screen.findByTestId('node-node-2');

      const node1AfterInitial = renderCountRef.current['node-1'];
      const node2AfterInitial = renderCountRef.current['node-2'];

      // Simulate multiple rapid position updates (like dragging)
      for (let i = 0; i < 5; i++) {
        await act(async () => {
          setNodesRef.current?.((nodes) =>
            nodes.map((n) =>
              n.id === 'node-1'
                ? { ...n, position: { x: i * 20, y: i * 20 } }
                : n
            )
          );
        });
      }

      // After position-only changes:
      // - Memoized nodes should have minimal additional renders
      // - The key insight is that memo DOES prevent unnecessary re-renders
      //   when props (id, data) don't change, even though ReactFlow updates position
      //
      // Note: Some re-renders may still occur due to ReactFlow's internal state management
      const node1AfterDrag = renderCountRef.current['node-1'];
      const node2AfterDrag = renderCountRef.current['node-2'];

      // THE KEY TEST: node-2 should not have re-rendered since its position didn't change
      // and it's memoized. This proves that memo prevents sibling re-renders.
      expect(node2AfterDrag).toBe(node2AfterInitial);

      // node-1 may have some renders due to ReactFlow internals (selected state, etc.)
      // but significantly less than 5 (the number of position updates)
      // This proves memo is helping even for the dragged node
      expect(node1AfterDrag - node1AfterInitial).toBeLessThan(5);
    });

    it('non-memoized component should re-render on every position change', async () => {
      const renderCountRef = { current: {} as Record<string, number> };

      // Create a NON-memoized node to show the difference
      const NonMemoizedTestNode = function NonMemoizedTestNode({ id }: { id: string }) {
        useEffect(() => {
          renderCountRef.current[id] = (renderCountRef.current[id] || 0) + 1;
        });
        return <div data-testid={`node-${id}`}>Node {id}</div>;
      };

      const testNodeTypes = { test: NonMemoizedTestNode };
      const setNodesRef = { current: null as ReturnType<typeof useNodesState>[1] | null };

      const initialNodes: Node[] = [
        { id: 'node-1', type: 'test', position: { x: 0, y: 0 }, data: {} },
      ];

      function TestGraph() {
        const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
        setNodesRef.current = setNodes;

        return (
          <ReactFlow
            nodes={nodes}
            edges={[]}
            onNodesChange={onNodesChange}
            nodeTypes={testNodeTypes}
          />
        );
      }

      render(
        <TestWrapper>
          <TestGraph />
        </TestWrapper>
      );

      await screen.findByTestId('node-node-1');
      const initialCount = renderCountRef.current['node-1'];

      // Simulate position updates
      for (let i = 0; i < 3; i++) {
        await act(async () => {
          setNodesRef.current?.((nodes) =>
            nodes.map((n) => ({ ...n, position: { x: i * 20, y: i * 20 } }))
          );
        });
      }

      // Non-memoized should re-render more
      expect(renderCountRef.current['node-1']).toBeGreaterThan(initialCount);
    });
  });

  describe('Real node component rendering', () => {
    it('should render KafkaSourceNode without error', async () => {
      const initialNodes: Node<KafkaSourceNodeData>[] = [
        {
          id: 'source-1',
          type: 'kafkaSource',
          position: { x: 0, y: 0 },
          data: { topicPath: '/dev/kafka/local/topics/test-topic' },
        },
      ];

      function TestGraph() {
        const [nodes, , onNodesChange] = useNodesState(initialNodes);
        return (
          <ReactFlow
            nodes={nodes}
            edges={[]}
            onNodesChange={onNodesChange}
            nodeTypes={nodeTypes}
          />
        );
      }

      render(
        <TestWrapper>
          <TestGraph />
        </TestWrapper>
      );

      expect(await screen.findByText('Kafka Source')).toBeInTheDocument();
    });

    it('should render KafkaSinkNode without error', async () => {
      const initialNodes: Node<KafkaSinkNodeData>[] = [
        {
          id: 'sink-1',
          type: 'kafkaSink',
          position: { x: 0, y: 0 },
          data: { topicName: 'output-topic' },
        },
      ];

      function TestGraph() {
        const [nodes, , onNodesChange] = useNodesState(initialNodes);
        return (
          <ReactFlow
            nodes={nodes}
            edges={[]}
            onNodesChange={onNodesChange}
            nodeTypes={nodeTypes}
          />
        );
      }

      render(
        <TestWrapper>
          <TestGraph />
        </TestWrapper>
      );

      expect(await screen.findByText('Kafka Sink')).toBeInTheDocument();
    });

    it('should handle rapid position updates without crashing (drag simulation)', async () => {
      const initialNodes: Node<KafkaSourceNodeData>[] = [
        {
          id: 'source-1',
          type: 'kafkaSource',
          position: { x: 0, y: 0 },
          data: { topicPath: '/dev/kafka/local/topics/test-topic' },
        },
      ];

      const setNodesRef = { current: null as ReturnType<typeof useNodesState<Node<KafkaSourceNodeData>>>[1] | null };

      function TestGraph() {
        const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
        setNodesRef.current = setNodes;
        return (
          <ReactFlow
            nodes={nodes}
            edges={[]}
            onNodesChange={onNodesChange}
            nodeTypes={nodeTypes}
          />
        );
      }

      render(
        <TestWrapper>
          <TestGraph />
        </TestWrapper>
      );

      expect(await screen.findByText('Kafka Source')).toBeInTheDocument();

      // Simulate rapid position updates (like fast dragging)
      for (let i = 0; i < 20; i++) {
        await act(async () => {
          setNodesRef.current?.((nodes: Node<KafkaSourceNodeData>[]) =>
            nodes.map((n) => ({
              ...n,
              position: { x: i * 10, y: i * 10 },
            }))
          );
        });
      }

      // Node should still be rendered correctly after all updates
      expect(screen.getByText('Kafka Source')).toBeInTheDocument();
    });

    it('should render multiple node types together', async () => {
      const initialNodes: Node[] = [
        {
          id: 'source-1',
          type: 'kafkaSource',
          position: { x: 0, y: 0 },
          data: { topicPath: '/dev/kafka/local/topics/test-topic' } as KafkaSourceNodeData,
        },
        {
          id: 'sink-1',
          type: 'kafkaSink',
          position: { x: 300, y: 0 },
          data: { topicName: 'output-topic' } as KafkaSinkNodeData,
        },
      ];

      function TestGraph() {
        const [nodes, , onNodesChange] = useNodesState(initialNodes);
        return (
          <ReactFlow
            nodes={nodes}
            edges={[]}
            onNodesChange={onNodesChange}
            nodeTypes={nodeTypes}
          />
        );
      }

      render(
        <TestWrapper>
          <TestGraph />
        </TestWrapper>
      );

      expect(await screen.findByText('Kafka Source')).toBeInTheDocument();
      expect(await screen.findByText('Kafka Sink')).toBeInTheDocument();
    });
  });
});
