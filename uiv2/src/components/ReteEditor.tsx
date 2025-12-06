import { useRef, useCallback, useState } from 'react';
import { useReteEditor } from '../hooks/useReteEditor';
import { useGraphJobSubmit } from '../hooks/useGraphJobSubmit';
import { serializeToPipelineGraph } from '../utils/graphSerializer';
import styled from 'styled-components';

const EditorContainer = styled.div`
  width: 100%;
  height: 600px;
  border: 1px solid #ccc;
  border-radius: 8px;
  background: #f5f5f5;
  position: relative;

  /* Rete.js canvas styling */
  .rete {
    background: #f5f5f5;

    /* Grid pattern */
    background-image:
      linear-gradient(rgba(0, 0, 0, 0.05) 1px, transparent 1px),
      linear-gradient(90deg, rgba(0, 0, 0, 0.05) 1px, transparent 1px);
    background-size: 20px 20px;
  }

  /* Node styling */
  .node {
    background: white;
    border: 2px solid #4a5568;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

    &.selected {
      border-color: #3182ce;
      box-shadow: 0 0 0 3px rgba(49, 130, 206, 0.2);
    }
  }

  /* Connection styling */
  .connection path {
    stroke: #4a5568;
    stroke-width: 2px;

    &.selected {
      stroke: #3182ce;
      stroke-width: 3px;
    }
  }

  /* Socket styling */
  .socket {
    background: #4a5568;
    border: 2px solid white;

    &.input {
      background: #48bb78;
    }

    &.output {
      background: #4299e1;
    }
  }

  /* Dock styling */
  .dock {
    background: white;
    border-right: 1px solid #ccc;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }
`;

const Toolbar = styled.div`
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1000;
  display: flex;
  gap: 8px;
`;

const Button = styled.button`
  padding: 8px 16px;
  background: #007acc;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;

  &:hover {
    background: #005a9e;
  }

  &:disabled {
    background: #555;
    cursor: not-allowed;
  }
`;

const ErrorMessage = styled.div`
  position: absolute;
  bottom: 10px;
  left: 10px;
  right: 10px;
  padding: 12px;
  background: #f44336;
  color: white;
  border-radius: 4px;
  z-index: 1000;
`;

const SuccessMessage = styled.div`
  position: absolute;
  bottom: 10px;
  left: 10px;
  right: 10px;
  padding: 12px;
  background: #4caf50;
  color: white;
  border-radius: 4px;
  z-index: 1000;
`;

export function ReteEditor({ onJobCreated }: {
  onJobCreated?: (jobId: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [hasChanges, setHasChanges] = useState(false);
  const graphJobMutation = useGraphJobSubmit();

  const handleNodesChange = useCallback(() => {
    setHasChanges(true);
  }, []);

  const editor = useReteEditor(containerRef.current, handleNodesChange);

  const handleSave = useCallback(async () => {
    if (!editor) {
      console.error('❌ Editor not initialized');
      return;
    }

    try {
      console.log('📊 Serializing graph...');
      const graph = serializeToPipelineGraph(editor);
      console.log('📦 Graph serialized:', {
        nodeCount: graph.nodes.length,
        edgeCount: graph.edges.length,
        nodes: graph.nodes.map(n => ({ id: n.id, type: n.nodeType?.case }))
      });

      console.log('🚀 Submitting to backend...');
      const response = await graphJobMutation.mutateAsync({
        userId: 'local',
        graph
      });

      console.log('✅ Job created:', response);
      setHasChanges(false);

      if (onJobCreated && response.jobId) {
        onJobCreated(response.jobId);
      }
    } catch (error) {
      console.error('❌ Failed to submit pipeline:', error);
      // Error will be displayed via graphJobMutation.isError
    }
  }, [editor, graphJobMutation, onJobCreated]);

  return (
    <EditorContainer>
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />

      <Toolbar>
        {!editor && <div style={{ background: 'yellow', padding: '4px' }}>Loading editor...</div>}
        {editor && <div style={{ background: 'lime', padding: '4px' }}>Editor ready!</div>}
        <Button onClick={handleSave} disabled={!editor || graphJobMutation.isPending}>
          {graphJobMutation.isPending ? 'Submitting...' : 'Save & Run'}
        </Button>
      </Toolbar>

      {graphJobMutation.isError && (
        <ErrorMessage>
          <strong>Failed to submit pipeline:</strong>
          <br />
          {graphJobMutation.error?.message || 'Unknown error'}
          {graphJobMutation.error?.cause && (
            <>
              <br />
              <small>{String(graphJobMutation.error.cause)}</small>
            </>
          )}
        </ErrorMessage>
      )}

      {graphJobMutation.isSuccess && graphJobMutation.data && (
        <SuccessMessage>
          <strong>Pipeline submitted successfully!</strong>
          <br />
          Job ID: {graphJobMutation.data.jobId}
        </SuccessMessage>
      )}
    </EditorContainer>
  );
}
