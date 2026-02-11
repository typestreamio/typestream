import type { Edge, Node } from '@xyflow/react';
import { PipelineGraph } from '../generated/job_pb';
import { serializeGraph } from './graphSerializer';
import { deserializeGraph } from './graphDeserializer';

/**
 * Pipeline file format (.typestream.json)
 */
export interface PipelineFileFormat {
  name: string;
  version?: string;
  description?: string;
  graph: Record<string, unknown>;
}

/**
 * Export the current graph as a .typestream.json pipeline file.
 * Prompts a file download in the browser.
 */
export function exportPipelineFile(
  nodes: Node[],
  edges: Edge[],
  name: string,
  description: string = '',
  version: string = '1'
): void {
  const graph = serializeGraph(nodes, edges);
  const graphJson = graph.toJson();

  const pipelineFile: PipelineFileFormat = {
    name,
    version,
    description,
    graph: graphJson as Record<string, unknown>,
  };

  const jsonString = JSON.stringify(pipelineFile, null, 2);
  const blob = new Blob([jsonString], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  const a = document.createElement('a');
  a.href = url;
  a.download = `${name}.typestream.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * Import a .typestream.json file and return the deserialized graph.
 * Returns null if the file is invalid.
 */
export function importPipelineFile(content: string): {
  nodes: Node[];
  edges: Edge[];
  name: string;
  version: string;
  description: string;
} | null {
  try {
    const parsed = JSON.parse(content) as PipelineFileFormat;

    if (!parsed.name || !parsed.graph) {
      return null;
    }

    const graph = PipelineGraph.fromJson(parsed.graph);
    const { nodes, edges } = deserializeGraph(graph);

    // Remove read-only flag since we're importing for editing
    const editableNodes = nodes.map((node) => ({
      ...node,
      data: { ...node.data, readOnly: undefined },
    }));

    return {
      nodes: editableNodes,
      edges,
      name: parsed.name,
      version: parsed.version || '1',
      description: parsed.description || '',
    };
  } catch {
    return null;
  }
}
