import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputIcon from '@mui/icons-material/Input';
import { BaseNode } from './BaseNode';
import { useKafkaTopics } from '../../../hooks/useKafkaTopics';
import { ENCODING_OPTIONS, type KafkaSourceNodeType } from './index';

export function KafkaSourceNode({ id, data }: NodeProps<KafkaSourceNodeType>) {
  const { topics } = useKafkaTopics();
  const { updateNodeData } = useReactFlow();

  return (
    <>
      <BaseNode title="Kafka Source" icon={<InputIcon fontSize="small" />}>
        <FormControl fullWidth size="small" sx={{ mb: 1.5 }} className="nodrag nowheel">
          <InputLabel>Topic</InputLabel>
          <Select
            value={data.topicPath}
            label="Topic"
            onChange={(e) => updateNodeData(id, { topicPath: e.target.value })}
          >
            {topics.map((topic) => (
              <MenuItem key={topic} value={`/dev/kafka/local/topics/${topic}`}>
                {topic}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Encoding</InputLabel>
          <Select
            value={data.encoding}
            label="Encoding"
            onChange={(e) => updateNodeData(id, { encoding: e.target.value })}
          >
            {ENCODING_OPTIONS.map((opt) => (
              <MenuItem key={opt.value} value={opt.value}>
                {opt.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
