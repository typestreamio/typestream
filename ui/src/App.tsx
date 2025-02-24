import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { KafkaTopics } from './components/KafkaTopics';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <div style={{ padding: '20px' }}>
        <h1>TypeStream</h1>
        <KafkaTopics />
      </div>
    </QueryClientProvider>
  );
}

export default App;
