import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import OutputIcon from '@mui/icons-material/Output';
import { BaseNode } from './BaseNode';
import type { KafkaSinkNodeType } from './index';

export function KafkaSinkNode({ id, data }: NodeProps<KafkaSinkNodeType>) {
  const { updateNodeData } = useReactFlow();

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="Kafka Sink" icon={<OutputIcon fontSize="small" />}>
        <TextField
          fullWidth
          size="small"
          label="New Topic Name"
          value={data.topicName}
          onChange={(e) => updateNodeData(id, { topicName: e.target.value })}
          placeholder="my-new-topic"
          className="nodrag nowheel"
        />
      </BaseNode>
    </>
  );
}
