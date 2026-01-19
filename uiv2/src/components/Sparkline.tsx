import { SparkLineChart } from '@mui/x-charts/SparkLineChart';
import Box from '@mui/material/Box';

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  showTooltip?: boolean;
}

/**
 * A sparkline component for visualizing time-series data.
 * Uses MUI X Charts SparkLineChart with tooltip support.
 */
export function Sparkline({
  data,
  width = 80,
  height = 24,
  color = '#646cff',
  showTooltip = true,
}: SparklineProps) {
  // Generate accessible label
  const getAriaLabel = () => {
    if (data.length === 0) {
      return 'Throughput chart: No data available';
    }
    const latest = data[data.length - 1];
    const trend = data.length > 1
      ? data[data.length - 1] > data[0] ? 'increasing' : data[data.length - 1] < data[0] ? 'decreasing' : 'stable'
      : 'stable';
    return `Throughput chart: ${latest.toFixed(1)} messages per second, trend ${trend}`;
  };

  // Show placeholder when no data
  if (data.length === 0) {
    return (
      <Box
        component="svg"
        role="img"
        aria-label={getAriaLabel()}
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
      role="img"
      aria-label={getAriaLabel()}
      sx={{ display: 'inline-block' }}
    >
      <SparkLineChart
        data={data}
        width={width}
        height={height}
        color={color}
        curve="natural"
        area
        showTooltip={showTooltip}
        showHighlight
        valueFormatter={(value) => `${value?.toFixed(1) ?? 0} msg/s`}
      />
    </Box>
  );
}
