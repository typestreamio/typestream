import { useState, useCallback } from 'react';
import { QueryProvider } from './providers/QueryProvider';
import { KafkaTopicBrowser } from './components/KafkaTopicBrowser';
import { GraphJobSubmitter } from './components/GraphJobSubmitter';
import { JobsList } from './components/JobsList';
import type { PipelineGraph } from './generated/job_pb';
import './App.css';

function App() {
  const [, setActiveJobGraph] = useState<PipelineGraph | null>(null);
  const [jobsKey, setJobsKey] = useState(0);

  const handleJobCreated = useCallback((jobId: string, graph: PipelineGraph) => {
    console.log(`Job created: ${jobId}`);
    setActiveJobGraph(graph);
    // Trigger jobs list refresh
    setJobsKey((k) => k + 1);
  }, []);

  return (
    <QueryProvider>
      <div style={{ padding: '20px' }}>
        <h1>TypeStream</h1>

        <section style={{ marginBottom: '40px' }}>
          <KafkaTopicBrowser />
        </section>

        <section style={{ marginBottom: '40px' }}>
          <GraphJobSubmitter onJobCreated={handleJobCreated} />
        </section>

        <section style={{ marginBottom: '40px' }}>
          <JobsList key={jobsKey} />
        </section>
      </div>
    </QueryProvider>
  );
}

export default App;
