import { useEffect, useMemo } from 'react';
import { useGraphJobSubmit } from '../hooks/useGraphJobSubmit';
import {
  CreateJobFromGraphRequest,
  PipelineGraph,
  PipelineNode,
  PipelineEdge,
  StreamSourceNode,
  FilterNode,
  DataStreamProto,
  PredicateProto,
  Encoding,
} from '../generated/job_pb';

interface GraphJobSubmitterProps {
  userId?: string;
  onJobCreated?: (jobId: string, graph: PipelineGraph) => void;
}

export function GraphJobSubmitter({ userId = 'local', onJobCreated }: GraphJobSubmitterProps) {
  const mutation = useGraphJobSubmit();

  const request = useMemo(() => {
    const sourceNode = new PipelineNode({
      id: 'source-1',
      nodeType: {
        case: 'streamSource',
        value: new StreamSourceNode({
          dataStream: new DataStreamProto({ path: '/dev/kafka/local/topics/books' }),
          encoding: Encoding.AVRO,
        }),
      },
    });

    const filterNode = new PipelineNode({
      id: 'filter-1',
      nodeType: {
        case: 'filter',
        value: new FilterNode({
          byKey: false,
          predicate: new PredicateProto({ expr: 'Station' }),
        }),
      },
    });

    const graph = new PipelineGraph({
      nodes: [sourceNode, filterNode],
      edges: [new PipelineEdge({ fromId: 'source-1', toId: 'filter-1' })],
    });

    return new CreateJobFromGraphRequest({ userId, graph });
  }, [userId]);

  useEffect(() => {
    if (mutation.isSuccess && mutation.data?.success && onJobCreated && request.graph) {
      onJobCreated(mutation.data.jobId, request.graph);
    }
  }, [mutation.isSuccess, mutation.data, onJobCreated, request.graph]);

  const handleSubmit = () => {
    mutation.mutate(request);
  };

  const jobSuccess = mutation.isSuccess && mutation.data?.success;
  const jobError = mutation.data?.error || (mutation.isError ? mutation.error.message : null);

  return (
    <div>
      <h2>Submit Visual Pipeline</h2>
      <p>Example: StreamSource(/dev/kafka/local/topics/books) → Filter("Station")</p>
      <button onClick={handleSubmit} disabled={mutation.isPending}>
        {mutation.isPending ? 'Submitting...' : 'Run Visual Pipeline'}
      </button>
      {jobSuccess && (
        <div style={{ color: 'green' }}>
          Job created successfully! ID: {mutation.data?.jobId}
        </div>
      )}
      {jobError && (
        <div style={{ color: 'red' }}>
          Error: {jobError}
        </div>
      )}
    </div>
  );
}
