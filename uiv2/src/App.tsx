import { useState } from 'react';
import { QueryProvider } from './providers/QueryProvider';
import { KafkaTopicBrowser } from './components/KafkaTopicBrowser';
import { ReteEditor } from './components/ReteEditor';
import { JobMonitor } from './components/JobMonitor';
import './App.css';

function App() {
  const [activeJobId, setActiveJobId] = useState<string | null>(null);

  const handleJobCreated = (jobId: string) => {
    console.log(`Job created: ${jobId}`);
    setActiveJobId(jobId);
  };

  return (
    <QueryProvider>
      <div style={{ padding: '20px' }}>
        <h1>TypeStream Visual Editor</h1>
        <p>Phase 6: Drag-and-drop pipeline builder with Rete.js</p>

        <section style={{ marginBottom: '40px' }}>
          <h2>Available Topics</h2>
          <KafkaTopicBrowser />
        </section>

        <section style={{ marginBottom: '40px' }}>
          <h2>Pipeline Editor</h2>
          <p>Drag nodes from the left sidebar onto the canvas to build your pipeline</p>
          <ReteEditor onJobCreated={handleJobCreated} />
        </section>

        {activeJobId && (
          <section style={{ marginBottom: '40px' }}>
            <h2>Job Output</h2>
            <JobMonitor
              pipelineType="graph"
              pipelineData={{ jobId: activeJobId }}
            />
          </section>
        )}
      </div>
    </QueryProvider>
  );
}

export default App;
