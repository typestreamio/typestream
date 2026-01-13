import { vi } from 'vitest';

// Mock transport for Connect RPC
export function createMockTransport() {
  return {
    unary: vi.fn(),
    stream: vi.fn(),
  };
}

// Mock successful preview job creation response
export const mockCreatePreviewJobSuccess = {
  success: true,
  jobId: 'test-job-123',
  inspectTopic: 'test-job-123-inspect-node1',
  error: '',
};

// Mock failed preview job creation response
export const mockCreatePreviewJobFailure = {
  success: false,
  jobId: '',
  inspectTopic: '',
  error: 'Failed to create preview job',
};

// Mock stream preview messages
export const mockStreamMessages = [
  { key: 'key1', value: '{"name": "test1"}', timestamp: BigInt(1234567890000) },
  { key: 'key2', value: '{"name": "test2"}', timestamp: BigInt(1234567891000) },
];
