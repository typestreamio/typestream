import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import DescriptionIcon from '@mui/icons-material/Description';
import { BaseNode } from './BaseNode';
import type { TextExtractorNodeType, NodeValidationState } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const textExtractorRole = 'transform' as const;

export function TextExtractorNode({ id, data }: NodeProps<TextExtractorNodeType>) {
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
        title="Text Extractor"
        icon={<DescriptionIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>File Path Field</InputLabel>
          <Select
            value={data.filePathField}
            label="File Path Field"
            onChange={(e) => updateNodeData(id, { filePathField: e.target.value })}
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
          placeholder="text"
        />
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
