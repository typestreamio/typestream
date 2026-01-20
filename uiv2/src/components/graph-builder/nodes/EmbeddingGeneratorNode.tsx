import { memo, useEffect } from 'react';
import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import MemoryIcon from '@mui/icons-material/Memory';
import { BaseNode } from './BaseNode';
import { isTypeCompatible, type EmbeddingGeneratorNodeType, type NodeValidationState } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const embeddingGeneratorRole = 'transform' as const;

export const EmbeddingGeneratorNode = memo(function EmbeddingGeneratorNode({ id, data }: NodeProps<EmbeddingGeneratorNodeType>) {
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

  // Get compatible fields for auto-selection (text field should be string type)
  const compatibleFields = fields.filter(f => isTypeCompatible(f.type, 'string'));

  // Auto-select first compatible field when schema loads and no field is selected
  useEffect(() => {
    if (!data.textField && compatibleFields.length > 0) {
      updateNodeData(id, { textField: compatibleFields[0].name });
    }
  }, [data.textField, compatibleFields, id, updateNodeData]);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title="Embedding Generator"
        icon={<MemoryIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>Text Field</InputLabel>
          <Select
            value={data.textField}
            label="Text Field"
            onChange={(e) => updateNodeData(id, { textField: e.target.value })}
            disabled={data.isInferring || fields.length === 0}
          >
            {fields.map((field) => {
              const compatible = isTypeCompatible(field.type, 'string');
              return (
                <MenuItem
                  key={field.name}
                  value={field.name}
                  disabled={!compatible}
                  sx={{ opacity: compatible ? 1 : 0.5 }}
                >
                  {field.name}
                  <Typography component="span" color="text.secondary" sx={{ ml: 1, fontSize: '0.75rem' }}>
                    ({field.type})
                  </Typography>
                </MenuItem>
              );
            })}
          </Select>
        </FormControl>
        <TextField
          fullWidth
          size="small"
          label="Output Field"
          value={data.outputField}
          onChange={(e) => updateNodeData(id, { outputField: e.target.value })}
          className="nodrag"
          placeholder="embedding"
          sx={{ mb: 1.5 }}
        />
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Model</InputLabel>
          <Select
            value={data.model}
            label="Model"
            onChange={(e) => updateNodeData(id, { model: e.target.value })}
          >
            <MenuItem value="text-embedding-3-small">text-embedding-3-small</MenuItem>
            <MenuItem value="text-embedding-3-large">text-embedding-3-large</MenuItem>
          </Select>
        </FormControl>
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
});
