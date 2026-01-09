import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import OutputIcon from '@mui/icons-material/Output';
import { BaseNode } from './BaseNode';
import { useKafkaTopics } from '../../../hooks/useKafkaTopics';
import { Encoding } from '../../../generated/job_pb';
import type { KafkaSinkNodeType } from './index';

function getEncodingLabel(encoding: Encoding): string {
  switch (encoding) {
    case Encoding.AVRO: return 'AVRO';
    case Encoding.JSON: return 'JSON';
    case Encoding.PROTOBUF: return 'PROTOBUF';
    case Encoding.STRING: return 'STRING';
    case Encoding.NUMBER: return 'NUMBER';
    default: return 'UNKNOWN';
  }
}

export function KafkaSinkNode({ id, data }: NodeProps<KafkaSinkNodeType>) {
  const { topics } = useKafkaTopics();
  const { updateNodeData } = useReactFlow();

  const selectedTopic = topics.find(
    (t) => `/dev/kafka/local/topics/${t.name}` === data.topicPath
  );
  const isExistingTopic = !!selectedTopic;

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="Kafka Sink" icon={<OutputIcon fontSize="small" />}>
        {isExistingTopic || !data.topicPath ? (
          <FormControl fullWidth size="small" className="nodrag nowheel">
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
                <MenuItem key={topic.name} value={`/dev/kafka/local/topics/${topic.name}`}>
                  {topic.name}
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
            className="nodrag nowheel"
          />
        )}
        {selectedTopic && (
          <Box sx={{ mt: 1, display: 'flex', justifyContent: 'flex-end' }}>
            <Chip
              label={getEncodingLabel(selectedTopic.encoding)}
              size="small"
              color="primary"
              variant="outlined"
            />
          </Box>
        )}
      </BaseNode>
    </>
  );
}
