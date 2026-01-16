/**
 * Format a throughput value (messages per second) for display.
 */
export function formatThroughput(value: number): string {
  if (value === 0) return '0 msg/s';
  if (value < 1) return `${value.toFixed(2)} msg/s`;
  if (value < 1000) return `${value.toFixed(1)} msg/s`;
  if (value < 1000000) return `${(value / 1000).toFixed(1)}K msg/s`;
  return `${(value / 1000000).toFixed(1)}M msg/s`;
}

/**
 * Format bytes for display (B, KB, MB, GB).
 */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const value = bytes / Math.pow(1024, i);
  return `${value.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

/**
 * Format a large number with commas for readability.
 */
export function formatNumber(value: number | bigint): string {
  return value.toLocaleString();
}
