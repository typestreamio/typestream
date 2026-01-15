import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TableChartIcon from '@mui/icons-material/TableChart';
import { BaseNode } from './BaseNode';
import type { MaterializedViewNodeType, NodeValidationState } from './index';

export type AggregationType = 'count' | 'latest';

export function MaterializedViewNode({ id, data }: NodeProps<MaterializedViewNodeType>) {
  const { updateNodeData } = useReactFlow();
  const nodes = useNodes();
  const edges = useEdges();

  // Find the upstream node to get its output schema
  const incomingEdge = edges.find((e) => e.target === id);
  const upstreamNode = incomingEdge
    ? nodes.find((n) => n.id === incomingEdge.source)
    : null;

  // Get fields from upstream node's computed output schema
  const upstreamData = upstreamNode?.data as NodeValidationState | undefined;
  const fields = upstreamData?.outputSchema ?? [];

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        title="Materialized View"
        icon={<TableChartIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>Aggregation</InputLabel>
          <Select
            value={data.aggregationType}
            label="Aggregation"
            onChange={(e) => updateNodeData(id, { aggregationType: e.target.value })}
          >
            <MenuItem value="count">Count</MenuItem>
            <MenuItem value="latest">Latest Value</MenuItem>
          </Select>
        </FormControl>
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Group By Field</InputLabel>
          <Select
            value={data.groupByField}
            label="Group By Field"
            onChange={(e) => updateNodeData(id, { groupByField: e.target.value })}
            disabled={data.isInferring || fields.length === 0}
          >
            {fields.map((field) => (
              <MenuItem key={field} value={field}>
                {field}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </BaseNode>
      {/* No output handle - this is a terminal node */}
    </>
  );
}
