import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import MemoryIcon from '@mui/icons-material/Memory';
import { BaseNode } from './BaseNode';
import { useTopicSchema } from '../../../hooks/useTopicSchema';
import type { EmbeddingGeneratorNodeType, KafkaSourceNodeData } from './index';

export function EmbeddingGeneratorNode({ id, data }: NodeProps<EmbeddingGeneratorNodeType>) {
  const { updateNodeData } = useReactFlow();
  const nodes = useNodes();
  const edges = useEdges();

  // Find the upstream source node to get the topic path for schema lookup
  const incomingEdge = edges.find((e) => e.target === id);
  const sourceNode = incomingEdge
    ? nodes.find((n) => n.id === incomingEdge.source)
    : null;

  // Get topic path from the source node (if it's a kafka source)
  const topicPath =
    sourceNode?.type === 'kafkaSource'
      ? (sourceNode.data as KafkaSourceNodeData).topicPath
      : '';

  const { fields, isLoading } = useTopicSchema(topicPath);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="Embedding Generator" icon={<MemoryIcon fontSize="small" />}>
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>Text Field</InputLabel>
          <Select
            value={data.textField}
            label="Text Field"
            onChange={(e) => updateNodeData(id, { textField: e.target.value })}
            disabled={isLoading || fields.length === 0}
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
}
