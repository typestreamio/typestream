import { useState, useEffect } from 'react';
import { useSession } from '../hooks/useSession';
import { useRunProgram } from '../hooks/useRunProgram';
import { useProgramOutput } from '../hooks/useProgramOutput';

interface JobMonitorProps {
  userId?: string;
  pipelineType: 'text' | 'graph';
  pipelineData?: string | any; // source text for text pipelines, graph data for graph pipelines
}

export function JobMonitor({ userId = 'local', pipelineType, pipelineData }: JobMonitorProps) {
  const [output, setOutput] = useState<string>('');
  const [isRunning, setIsRunning] = useState(false);
  const [programId, setProgramId] = useState<string>('');

  const sessionMutation = useSession();
  const runProgramMutation = useRunProgram();
  const outputQuery = useProgramOutput(
    sessionMutation.data?.sessionId || '',
    programId
  );

  useEffect(() => {
    if (outputQuery.data) {
      const newOutput = outputQuery.data.stdOut || outputQuery.data.stdErr || '';
      if (newOutput) {
        setOutput(prev => prev + newOutput + '\n');
      }
    }
  }, [outputQuery.data]);

  const startMonitoring = async (sessionId: string) => {
    setIsRunning(true);
    setOutput('');

    try {
      if (pipelineType === 'text' && typeof pipelineData === 'string') {
        // Run text-based program
        const result = await runProgramMutation.mutateAsync({
          sessionId,
          source: pipelineData,
        });
        setProgramId(result.id);
      } else if (pipelineType === 'graph' && pipelineData) {
        // For graph pipelines, we'd need a separate RPC
        // For now, we'll show a placeholder
        setOutput('Graph pipeline output monitoring coming soon...');
        setIsRunning(false);
      }
    } catch (error) {
      setOutput(`Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
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

      {isRunning && <div>Running... (streaming output)</div>}

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
