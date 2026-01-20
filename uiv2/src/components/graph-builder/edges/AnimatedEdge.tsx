import {
  getSmoothStepPath,
  type EdgeProps,
  BaseEdge,
} from '@xyflow/react';
import { useTheme } from '@mui/material/styles';

/**
 * AnimatedEdge displays a flowing dashed line animation to indicate data flow.
 * Used when a job is running to show active data movement.
 */
export function AnimatedEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style = {},
  markerEnd,
}: EdgeProps) {
  const theme = useTheme();
  const [edgePath] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  return (
    <>
      {/* CSS animation for the dashed line */}
      <style>
        {`
          @keyframes dashdraw {
            from {
              stroke-dashoffset: 10;
            }
            to {
              stroke-dashoffset: 0;
            }
          }
        `}
      </style>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...style,
          strokeWidth: 2,
          stroke: theme.palette.primary.main,
          strokeDasharray: '5 5',
          animation: 'dashdraw 0.5s linear infinite',
        }}
      />
    </>
  );
}
