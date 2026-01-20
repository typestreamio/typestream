import { memo, useState } from 'react';
import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import OutputIcon from '@mui/icons-material/Output';
import { BaseNode } from './BaseNode';
import { NodeSnapshotPanel } from '../../NodeSnapshotPanel';
import type { KafkaSinkNodeType } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const kafkaSinkRole = 'sink' as const;

export const KafkaSinkNode = memo(function KafkaSinkNode({ id, data }: NodeProps<KafkaSinkNodeType>) {
  const { updateNodeData } = useReactFlow();
  const [previewPanelOpen, setPreviewPanelOpen] = useState(false);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title="Kafka Sink"
        icon={<OutputIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
        onPreviewClick={() => setPreviewPanelOpen(true)}
      >
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

      <NodeSnapshotPanel
        open={previewPanelOpen}
        onClose={() => setPreviewPanelOpen(false)}
        nodeId={id}
        nodeTitle="Kafka Sink"
      />
    </>
  );
});
