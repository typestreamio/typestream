import { useState } from 'react';
import { useSession } from '../hooks/useSession';
import { useRunProgram } from '../hooks/useRunProgram';

interface JobMonitorProps {
  userId?: string;
  pipelineType: 'text' | 'graph';
  pipelineSource?: string;
}

export function JobMonitor({ userId = 'local', pipelineType, pipelineSource }: JobMonitorProps) {
  const [output, setOutput] = useState<string>('');
  const [isRunning, setIsRunning] = useState(false);

  const sessionMutation = useSession();
  const runProgramMutation = useRunProgram();

  const startMonitoring = async (sessionId: string) => {
    setIsRunning(true);
    setOutput('');

    try {
      if (pipelineType === 'text' && pipelineSource) {
        const result = await runProgramMutation.mutateAsync({
          sessionId,
          source: pipelineSource,
        });
        setOutput(`Program started with ID: ${result.id}\n\nNote: Real-time output streaming requires WebSocket support.`);
      } else if (pipelineType === 'graph') {
        setOutput('Graph pipeline submitted successfully.\n\nNote: Output monitoring for graph pipelines coming soon.');
      }
    } catch (error) {
      setOutput(`Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setIsRunning(false);
    }
  };

  const handleStartSession = async () => {
    try {
      const result = await sessionMutation.mutateAsync({ userId });
      await startMonitoring(result.sessionId);
    } catch (error) {
      setOutput(`Error starting session: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  };

  return (
    <div>
      <h2>Job Output Monitor</h2>

      {!isRunning && !output && (
        <button onClick={handleStartSession} disabled={sessionMutation.isPending}>
          {sessionMutation.isPending ? 'Starting session...' : 'Start Monitoring'}
        </button>
      )}

      {isRunning && <div>Running...</div>}

      {sessionMutation.isError && (
        <div style={{ color: 'red' }}>
          Error: {sessionMutation.error.message}
        </div>
      )}

      {runProgramMutation.isError && (
        <div style={{ color: 'red' }}>
          Error: {runProgramMutation.error.message}
        </div>
      )}

      {output && (
        <div style={{ marginTop: '1rem' }}>
          <h3>Output:</h3>
          <pre
            style={{
              backgroundColor: '#f5f5f5',
              padding: '1rem',
              borderRadius: '4px',
              maxHeight: '400px',
              overflow: 'auto',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            {output}
          </pre>
        </div>
      )}
    </div>
  );
}
