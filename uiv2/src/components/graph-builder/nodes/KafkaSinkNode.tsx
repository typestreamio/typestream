import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import OutputIcon from '@mui/icons-material/Output';
import { BaseNode } from './BaseNode';
import { useKafkaTopics } from '../../../hooks/useKafkaTopics';
import { ENCODING_OPTIONS, type KafkaSinkNodeType } from './index';

export function KafkaSinkNode({ id, data }: NodeProps<KafkaSinkNodeType>) {
  const { topics } = useKafkaTopics();
  const { updateNodeData } = useReactFlow();

  const isExistingTopic = topics.some((t) => `/dev/kafka/local/topics/${t}` === data.topicPath);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="Kafka Sink" icon={<OutputIcon fontSize="small" />}>
        {isExistingTopic || !data.topicPath ? (
          <FormControl fullWidth size="small" sx={{ mb: 1.5 }} className="nodrag nowheel">
            <InputLabel>Topic</InputLabel>
            <Select
              value={data.topicPath}
              label="Topic"
              onChange={(e) => updateNodeData(id, { topicPath: e.target.value })}
            >
              <MenuItem value="">
                <em>Enter custom path...</em>
              </MenuItem>
              {topics.map((topic) => (
                <MenuItem key={topic} value={`/dev/kafka/local/topics/${topic}`}>
                  {topic}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        ) : (
          <TextField
            fullWidth
            size="small"
            label="Topic Path"
            value={data.topicPath}
            onChange={(e) => updateNodeData(id, { topicPath: e.target.value })}
            placeholder="/dev/kafka/local/topics/..."
            sx={{ mb: 1.5 }}
            className="nodrag nowheel"
          />
        )}
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
    </>
  );
}
