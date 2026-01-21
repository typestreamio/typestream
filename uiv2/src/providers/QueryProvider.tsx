import { TransportProvider } from '@connectrpc/connect-query';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { transport } from '../services/transport';
import { ServerConnectionProvider } from './ServerConnectionProvider';
import React from 'react';

const queryClient = new QueryClient();

export function QueryProvider({ children }: { children: React.ReactNode }) {
  return (
    <TransportProvider transport={transport}>
      <QueryClientProvider client={queryClient}>
        <ServerConnectionProvider>{children}</ServerConnectionProvider>
      </QueryClientProvider>
    </TransportProvider>
  );
}
