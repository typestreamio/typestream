import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { KafkaTopics } from './KafkaTopics';
import { fileSystemClient } from '../services/grpc-client';

// Mock the grpc client
vi.mock('../services/grpc-client', () => ({
  fileSystemClient: {
    Ls: vi.fn()
  }
}));

describe('KafkaTopics', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    // Reset mocks before each test
    vi.clearAllMocks();
    
    // Create a new QueryClient for each test
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
  });

  const renderComponent = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <KafkaTopics />
      </QueryClientProvider>
    );
  };

  it('shows loading state initially', () => {
    // Mock the Ls function to never resolve
    vi.mocked(fileSystemClient.Ls).mockImplementation(() => new Promise(() => {}));
    
    renderComponent();
    expect(screen.getByText('Loading topics...')).toBeInTheDocument();
  });

  it('shows error state when request fails', async () => {
    // Mock the Ls function to return an error
    vi.mocked(fileSystemClient.Ls).mockResolvedValue({
      error: 'Failed to fetch topics',
      files: []
    });
    
    renderComponent();
    expect(await screen.findByText('Error: Failed to fetch topics')).toBeInTheDocument();
  });

  it('shows empty state when no topics are found', async () => {
    // Mock the Ls function to return empty array
    vi.mocked(fileSystemClient.Ls).mockResolvedValue({
      error: "",
      files: []
    });
    
    renderComponent();
    expect(await screen.findByText('No topics found')).toBeInTheDocument();
  });

  it('renders list of topics when data is available', async () => {
    const mockTopics = ['topic1', 'topic2', 'topic3'];
    
    // Mock the Ls function to return topics
    vi.mocked(fileSystemClient.Ls).mockResolvedValue({
      error: "",
      files: mockTopics
    });
    
    renderComponent();
    
    // Verify header is present
    expect(await screen.findByText('Kafka Topics')).toBeInTheDocument();
    
    // Verify all topics are rendered
    mockTopics.forEach(topic => {
      expect(screen.getByText(topic)).toBeInTheDocument();
    });
  });
});
