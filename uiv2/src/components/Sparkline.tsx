import { useMemo } from 'react';
import Box from '@mui/material/Box';

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  fillOpacity?: number;
}

/**
 * A lightweight SVG sparkline component for visualizing time-series data.
 * Renders a polyline with optional gradient fill below the line.
 */
export function Sparkline({
  data,
  width = 80,
  height = 24,
  color = '#646cff',
  fillOpacity = 0.2,
}: SparklineProps) {
  const { linePoints, fillPath } = useMemo(() => {
    if (data.length === 0) {
      return { linePoints: '', fillPath: '' };
    }

    // Find max value for scaling (minimum of 1 to avoid division by zero)
    const max = Math.max(...data, 1);

    // Calculate points
    const points = data.map((value, index) => {
      const x = (index / Math.max(data.length - 1, 1)) * width;
      const y = height - (value / max) * height * 0.9; // 90% height to leave margin
      return { x, y };
    });

    // Create polyline points string
    const linePoints = points.map((p) => `${p.x},${p.y}`).join(' ');

    // Create closed path for fill (line + bottom edge)
    const fillPath = `M 0,${height} L ${linePoints} L ${width},${height} Z`;

    return { linePoints, fillPath };
  }, [data, width, height]);

  // Show placeholder line when no data
  if (data.length === 0) {
    return (
      <Box
        component="svg"
        width={width}
        height={height}
        sx={{ display: 'block' }}
      >
        <line
          x1={0}
          y1={height / 2}
          x2={width}
          y2={height / 2}
          stroke={color}
          strokeOpacity={0.3}
          strokeWidth={1}
          strokeDasharray="2 2"
        />
      </Box>
    );
  }

  return (
    <Box
      component="svg"
      width={width}
      height={height}
      sx={{ display: 'block' }}
    >
      {/* Gradient fill under the line */}
      <path
        d={fillPath}
        fill={color}
        fillOpacity={fillOpacity}
      />
      {/* The sparkline */}
      <polyline
        points={linePoints}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Box>
  );
}
