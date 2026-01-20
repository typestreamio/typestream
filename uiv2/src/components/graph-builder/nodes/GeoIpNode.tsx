import { memo, useEffect, useState } from 'react';
import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import PublicIcon from '@mui/icons-material/Public';
import { BaseNode } from './BaseNode';
import { NodeSnapshotPanel } from '../../NodeSnapshotPanel';
import { isTypeCompatible, type GeoIpNodeType, type NodeValidationState } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const geoIpRole = 'transform' as const;

export const GeoIpNode = memo(function GeoIpNode({ id, data }: NodeProps<GeoIpNodeType>) {
  const { updateNodeData } = useReactFlow();
  const nodes = useNodes();
  const edges = useEdges();
  const [previewPanelOpen, setPreviewPanelOpen] = useState(false);

  // Find the upstream node to get its output schema
  const incomingEdge = edges.find((e) => e.target === id);
  const upstreamNode = incomingEdge
    ? nodes.find((n) => n.id === incomingEdge.source)
    : null;

  // Get fields from upstream node's computed output schema
  const upstreamData = upstreamNode?.data as NodeValidationState | undefined;
  const fields = upstreamData?.outputSchema ?? [];

  // Get compatible fields for auto-selection (IP field should be string type)
  const compatibleFields = fields.filter(f => isTypeCompatible(f.type, 'string'));

  // Auto-select first compatible field when schema loads and no field is selected
  useEffect(() => {
    if (!data.ipField && compatibleFields.length > 0) {
      updateNodeData(id, { ipField: compatibleFields[0].name });
    }
  }, [data.ipField, compatibleFields, id, updateNodeData]);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title="GeoIP Lookup"
        icon={<PublicIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
        onPreviewClick={() => setPreviewPanelOpen(true)}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>IP Field</InputLabel>
          <Select
            value={data.ipField}
            label="IP Field"
            onChange={(e) => updateNodeData(id, { ipField: e.target.value })}
            disabled={data.isInferring || fields.length === 0}
          >
            {fields.map((field) => {
              const compatible = isTypeCompatible(field.type, 'string');
              return (
                <MenuItem
                  key={field.name}
                  value={field.name}
                  disabled={!compatible}
                  sx={{ opacity: compatible ? 1 : 0.5 }}
                >
                  {field.name}
                  <Typography component="span" color="text.secondary" sx={{ ml: 1, fontSize: '0.75rem' }}>
                    ({field.type})
                  </Typography>
                </MenuItem>
              );
            })}
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

      <NodeSnapshotPanel
        open={previewPanelOpen}
        onClose={() => setPreviewPanelOpen(false)}
        nodeId={id}
        nodeTitle="GeoIP Lookup"
      />
    </>
  );
});
