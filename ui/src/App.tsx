import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReteCanvas } from './components/ReteCanvas';
import { TopicStreamer } from './components/TopicStreamer';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <div style={{ padding: '20px' }}>
        <h1>TypeStream</h1>
        <div style={{ marginBottom: '20px' }}>
          <ReteCanvas />
        </div>
        <TopicStreamer />
      </div>
    </QueryClientProvider>
  );
}

export default App;
