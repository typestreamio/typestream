import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { KafkaTopics } from './KafkaTopics';

describe('KafkaTopics Integration', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
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

  it('should show _schemas topic', async () => {
    renderComponent();
    
    // Wait for the _schemas topic to appear
    const schemasTopic = await screen.findByText('_schemas', {}, { timeout: 5000 });
    expect(schemasTopic).toBeInTheDocument();
  });
});
