import { Handle, Position, type NodeProps } from '@xyflow/react';
import Button from '@mui/material/Button';
import VisibilityIcon from '@mui/icons-material/Visibility';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { useState } from 'react';
import { BaseNode } from './BaseNode';
import { StreamInspectorPanel } from '../../StreamInspectorPanel';
import type { InspectorNodeType } from './index';

export function InspectorNode({ id, data }: NodeProps<InspectorNodeType>) {
  const [panelOpen, setPanelOpen] = useState(false);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        title="Inspector"
        icon={<VisibilityIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
      >
        <Button
          fullWidth
          size="small"
          variant="outlined"
          startIcon={<PlayArrowIcon />}
          onClick={() => setPanelOpen(true)}
        >
          Preview
        </Button>
      </BaseNode>

      <StreamInspectorPanel
        open={panelOpen}
        onClose={() => setPanelOpen(false)}
        nodeId={id}
      />
    </>
  );
}
