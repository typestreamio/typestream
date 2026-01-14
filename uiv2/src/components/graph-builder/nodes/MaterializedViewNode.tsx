import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TableChartIcon from '@mui/icons-material/TableChart';
import { BaseNode } from './BaseNode';
import { useTopicSchema } from '../../../hooks/useTopicSchema';
import type { MaterializedViewNodeType, KafkaSourceNodeData } from './index';

export type AggregationType = 'count' | 'latest';

export function MaterializedViewNode({ id, data }: NodeProps<MaterializedViewNodeType>) {
  const { updateNodeData } = useReactFlow();
  const nodes = useNodes();
  const edges = useEdges();

  // Find upstream source to get schema
  const incomingEdge = edges.find((e) => e.target === id);
  const sourceNode = incomingEdge ? nodes.find((n) => n.id === incomingEdge.source) : null;
  const topicPath = sourceNode?.type === 'kafkaSource'
    ? (sourceNode.data as KafkaSourceNodeData).topicPath
    : '';

  const { fields, isLoading } = useTopicSchema(topicPath);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="Materialized View" icon={<TableChartIcon fontSize="small" />}>
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>Aggregation</InputLabel>
          <Select
            value={data.aggregationType}
            label="Aggregation"
            onChange={(e) => updateNodeData(id, { aggregationType: e.target.value })}
          >
            <MenuItem value="count">Count</MenuItem>
            <MenuItem value="latest">Latest Value</MenuItem>
          </Select>
        </FormControl>
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Group By Field</InputLabel>
          <Select
            value={data.groupByField}
            label="Group By Field"
            onChange={(e) => updateNodeData(id, { groupByField: e.target.value })}
            disabled={isLoading || fields.length === 0}
          >
            {fields.map((field) => (
              <MenuItem key={field} value={field}>
                {field}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </BaseNode>
      {/* No output handle - this is a terminal node */}
    </>
  );
}
