import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import DescriptionIcon from '@mui/icons-material/Description';
import { BaseNode } from './BaseNode';
import { useTopicSchema } from '../../../hooks/useTopicSchema';
import type { TextExtractorNodeType, KafkaSourceNodeData } from './index';

export function TextExtractorNode({ id, data }: NodeProps<TextExtractorNodeType>) {
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
      <BaseNode title="Text Extractor" icon={<DescriptionIcon fontSize="small" />}>
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>File Path Field</InputLabel>
          <Select
            value={data.filePathField}
            label="File Path Field"
            onChange={(e) => updateNodeData(id, { filePathField: e.target.value })}
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
          placeholder="text"
        />
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
