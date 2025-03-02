import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReteCanvas } from './components/ReteCanvas';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <div style={{ padding: '20px' }}>
        <h1>TypeStream</h1>
        <ReteCanvas />
      </div>
    </QueryClientProvider>
  );
}

export default App;
