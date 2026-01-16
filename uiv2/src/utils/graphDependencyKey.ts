import type { AppNode, NodeValidationState } from '../components/graph-builder/nodes';

/**
 * Create a stable key from node/edge data for dependency tracking.
 * Excludes validation state fields (outputSchema, schemaError, isInferring)
 * that are set by schema inference to avoid infinite loops.
 * Also excludes position changes (node dragging) by not including position in the key.
 * Sorts by ID for consistent ordering regardless of array order.
 */
export function getGraphDependencyKey(
  nodes: AppNode[],
  edges: { source: string; target: string }[]
): string {
  const nodeData = nodes
    .map((n) => {
      // Intentionally exclude validation state fields to avoid infinite loops
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { outputSchema, schemaError, isInferring, ...rest } = n.data as NodeValidationState &
        Record<string, unknown>;
      return { id: n.id, type: n.type, data: rest };
    })
    .sort((a, b) => a.id.localeCompare(b.id)); // Sort for consistent ordering

  const edgeData = edges
    .map((e) => ({ source: e.source, target: e.target }))
    .sort((a, b) => a.source.localeCompare(b.source) || a.target.localeCompare(b.target));

  return JSON.stringify({ nodes: nodeData, edges: edgeData });
}
