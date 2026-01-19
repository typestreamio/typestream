import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import InputIcon from '@mui/icons-material/Input';
import { BaseNode } from './BaseNode';
import { useKafkaTopics } from '../../../hooks/useKafkaTopics';
import { Encoding } from '../../../generated/job_pb';
import type { KafkaSourceNodeType } from './index';

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

export function KafkaSourceNode({ id, data }: NodeProps<KafkaSourceNodeType>) {
  const { topics } = useKafkaTopics();
  const { updateNodeData } = useReactFlow();

  const selectedTopic = topics.find(
    (t) => `/dev/kafka/local/topics/${t.name}` === data.topicPath
  );

  // Check if topic name suggests it's a CDC topic (Debezium convention)
  const isCdcTopic = selectedTopic?.name.includes('.') || selectedTopic?.name.includes('debezium');

  return (
    <>
      <BaseNode
        title="Kafka Source"
        icon={<InputIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Topic</InputLabel>
          <Select
            value={data.topicPath}
            label="Topic"
            onChange={(e) => updateNodeData(id, { topicPath: e.target.value })}
          >
            {topics.map((topic) => (
              <MenuItem key={topic.name} value={`/dev/kafka/local/topics/${topic.name}`}>
                {topic.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {selectedTopic && (
          <Box sx={{ mt: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            {isCdcTopic && (
              <Tooltip title="Extract 'after' payload from Debezium CDC envelope for easier field access">
                <FormControlLabel
                  control={
                    <Checkbox
                      size="small"
                      checked={data.unwrapCdc ?? false}
                      onChange={(e) => updateNodeData(id, { unwrapCdc: e.target.checked })}
                    />
                  }
                  label="Unwrap CDC"
                  componentsProps={{ typography: { variant: 'caption' } }}
                  className="nodrag"
                  sx={{ ml: 0, mr: 1 }}
                />
              </Tooltip>
            )}
            <Chip
              label={getEncodingLabel(selectedTopic.encoding)}
              size="small"
              color="primary"
              variant="outlined"
            />
          </Box>
        )}
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
