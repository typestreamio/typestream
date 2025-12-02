import { useState } from 'react';
import { QueryProvider } from './providers/QueryProvider';
import { KafkaTopicBrowser } from './components/KafkaTopicBrowser';
import { GraphJobSubmitter } from './components/GraphJobSubmitter';
import { JobMonitor } from './components/JobMonitor';
import './App.css';

type ActiveJob = {
  data?: any;
} | null;

function App() {
  const [activeJob, setActiveJob] = useState<ActiveJob>(null);
  const [showMonitor, setShowMonitor] = useState(false);

  const handleJobCreated = (jobId: string, data?: any) => {
    console.log(`Job created: ${jobId}`);
    setActiveJob({ data });
    setShowMonitor(true);
  };

  return (
    <QueryProvider>
      <div style={{ padding: '20px' }}>
        <h1>TypeStream UI v2</h1>
        <p>Phase 5: Visual Pipeline Builder</p>

        <section style={{ marginBottom: '40px' }}>
          <KafkaTopicBrowser />
        </section>

        <section style={{ marginBottom: '40px' }}>
          <GraphJobSubmitter onJobCreated={handleJobCreated} />
        </section>

        {showMonitor && activeJob && (
          <section style={{ marginBottom: '40px' }}>
            <JobMonitor
              pipelineType="graph"
              pipelineData={activeJob.data}
            />
          </section>
        )}
      </div>
    </QueryProvider>
  );
}

export default App;
