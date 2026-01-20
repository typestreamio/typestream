import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import PublicIcon from '@mui/icons-material/Public';
import { BaseNode } from './BaseNode';
import type { GeoIpNodeType, NodeValidationState } from './index';

// Handle configuration for this node type
export const geoIpHandles = { hasInput: true, hasOutput: true } as const;

export function GeoIpNode({ id, data }: NodeProps<GeoIpNodeType>) {
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
        title="GeoIP Lookup"
        icon={<PublicIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>IP Field</InputLabel>
          <Select
            value={data.ipField}
            label="IP Field"
            onChange={(e) => updateNodeData(id, { ipField: e.target.value })}
            disabled={data.isInferring || fields.length === 0}
          >
            {fields.map((field) => (
              <MenuItem key={field} value={field}>
                {field}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField
          fullWidth
          size="small"
          label="Output Field"
          value={data.outputField}
          onChange={(e) => updateNodeData(id, { outputField: e.target.value })}
          className="nodrag"
          placeholder="country_code"
        />
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
