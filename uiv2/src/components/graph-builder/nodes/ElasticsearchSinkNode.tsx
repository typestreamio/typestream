import { memo } from 'react';
import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import SearchIcon from '@mui/icons-material/Search';
import { BaseNode } from './BaseNode';
import type { ElasticsearchSinkNodeType } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const elasticsearchSinkRole = 'sink' as const;

export const ElasticsearchSinkNode = memo(function ElasticsearchSinkNode({ id, data }: NodeProps<ElasticsearchSinkNodeType>) {
  const { updateNodeData } = useReactFlow();

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title={data.connectionName}
        icon={<SearchIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
          Elasticsearch
        </Typography>
        <TextField
          fullWidth
          size="small"
          label="Index Name"
          value={data.indexName}
          onChange={(e) => updateNodeData(id, { indexName: e.target.value })}
          placeholder="my-index"
          className="nodrag nowheel"
          sx={{ mb: 1 }}
          required
          error={!data.indexName || data.indexName.trim().length === 0}
          helperText={!data.indexName || data.indexName.trim().length === 0 ? 'Index name is required' : undefined}
        />
        <TextField
          fullWidth
          size="small"
          select
          label="Document ID Strategy"
          value={data.documentIdStrategy}
          onChange={(e) => updateNodeData(id, { documentIdStrategy: e.target.value })}
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        >
          <MenuItem value="RECORD_KEY">Use Kafka Key</MenuItem>
          <MenuItem value="TOPIC_PARTITION_OFFSET">Auto-generate (Topic/Partition/Offset)</MenuItem>
        </TextField>
        <TextField
          fullWidth
          size="small"
          select
          label="Write Method"
          value={data.writeMethod}
          onChange={(e) => updateNodeData(id, { writeMethod: e.target.value })}
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        >
          <MenuItem value="INSERT">Insert</MenuItem>
          <MenuItem value="UPSERT">Upsert</MenuItem>
        </TextField>
        <TextField
          fullWidth
          size="small"
          select
          label="On Null Values"
          value={data.behaviorOnNullValues}
          onChange={(e) => updateNodeData(id, { behaviorOnNullValues: e.target.value })}
          className="nodrag nowheel"
        >
          <MenuItem value="IGNORE">Ignore</MenuItem>
          <MenuItem value="DELETE">Delete</MenuItem>
          <MenuItem value="FAIL">Fail</MenuItem>
        </TextField>
      </BaseNode>
    </>
  );
});
