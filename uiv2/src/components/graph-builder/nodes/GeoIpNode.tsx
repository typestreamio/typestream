import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import PublicIcon from '@mui/icons-material/Public';
import { BaseNode } from './BaseNode';
import { useTopicSchema } from '../../../hooks/useTopicSchema';
import type { GeoIpNodeType, KafkaSourceNodeData } from './index';

export function GeoIpNode({ id, data }: NodeProps<GeoIpNodeType>) {
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
      <BaseNode title="GeoIP Lookup" icon={<PublicIcon fontSize="small" />}>
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>IP Field</InputLabel>
          <Select
            value={data.ipField}
            label="IP Field"
            onChange={(e) => updateNodeData(id, { ipField: e.target.value })}
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
          placeholder="country_code"
        />
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
