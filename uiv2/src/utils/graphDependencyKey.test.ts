import { describe, it, expect } from 'vitest';
import { getGraphDependencyKey } from './graphDependencyKey';
import type { AppNode } from '../components/graph-builder/nodes';

describe('getGraphDependencyKey', () => {
  it('should create a stable key from nodes and edges', () => {
    const nodes: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '/dev/kafka/local/topics/web_visits' },
      },
      {
        id: 'node-2',
        type: 'geoIp',
        position: { x: 200, y: 0 },
        data: { ipField: 'ip', outputField: 'country_code' },
      },
    ];
    const edges = [{ source: 'node-1', target: 'node-2' }];

    const key = getGraphDependencyKey(nodes, edges);
    expect(key).toContain('node-1');
    expect(key).toContain('kafkaSource');
    expect(key).toContain('web_visits');
    expect(key).toContain('geoIp');
  });

  it('should exclude validation state fields (outputSchema, schemaError, isInferring)', () => {
    const nodes: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: {
          topicPath: '/dev/kafka/local/topics/web_visits',
          outputSchema: ['ip', 'url', 'timestamp'],
          schemaError: undefined,
          isInferring: false,
        },
      },
    ];
    const edges: { source: string; target: string }[] = [];

    const key = getGraphDependencyKey(nodes, edges);
    // Key should NOT contain validation state fields
    expect(key).not.toContain('outputSchema');
    expect(key).not.toContain('schemaError');
    expect(key).not.toContain('isInferring');
    // But should still contain the actual data
    expect(key).toContain('topicPath');
    expect(key).toContain('web_visits');
  });

  it('should produce different keys when node data changes', () => {
    const nodesV1: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '' }, // Empty topic
      },
    ];
    const nodesV2: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '/dev/kafka/local/topics/web_visits' }, // Selected topic
      },
    ];
    const edges: { source: string; target: string }[] = [];

    const key1 = getGraphDependencyKey(nodesV1, edges);
    const key2 = getGraphDependencyKey(nodesV2, edges);

    expect(key1).not.toBe(key2);
  });

  it('should produce different keys when edges change', () => {
    const nodes: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '/dev/kafka/local/topics/web_visits' },
      },
      {
        id: 'node-2',
        type: 'geoIp',
        position: { x: 200, y: 0 },
        data: { ipField: 'ip', outputField: 'country_code' },
      },
    ];

    const edgesEmpty: { source: string; target: string }[] = [];
    const edgesConnected = [{ source: 'node-1', target: 'node-2' }];

    const key1 = getGraphDependencyKey(nodes, edgesEmpty);
    const key2 = getGraphDependencyKey(nodes, edgesConnected);

    expect(key1).not.toBe(key2);
  });

  it('should produce same key when only validation state fields change', () => {
    const nodesV1: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: {
          topicPath: '/dev/kafka/local/topics/web_visits',
          isInferring: true, // Inferring
        },
      },
    ];
    const nodesV2: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: {
          topicPath: '/dev/kafka/local/topics/web_visits',
          isInferring: false, // Done inferring
          outputSchema: ['ip', 'url'],
        },
      },
    ];
    const edges: { source: string; target: string }[] = [];

    const key1 = getGraphDependencyKey(nodesV1, edges);
    const key2 = getGraphDependencyKey(nodesV2, edges);

    // Keys should be the same since only validation state changed
    expect(key1).toBe(key2);
  });

  it('should handle chained transformation nodes', () => {
    const nodes: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '/dev/kafka/local/topics/file_uploads' },
      },
      {
        id: 'node-2',
        type: 'textExtractor',
        position: { x: 200, y: 0 },
        data: { filePathField: 'file_path', outputField: 'text' },
      },
      {
        id: 'node-3',
        type: 'embeddingGenerator',
        position: { x: 400, y: 0 },
        data: { textField: 'text', outputField: 'embedding', model: 'text-embedding-3-small' },
      },
    ];
    const edges = [
      { source: 'node-1', target: 'node-2' },
      { source: 'node-2', target: 'node-3' },
    ];

    const key = getGraphDependencyKey(nodes, edges);
    expect(key).toContain('textExtractor');
    expect(key).toContain('embeddingGenerator');
    expect(key).toContain('text-embedding-3-small');
  });

  it('should produce same key regardless of node array order', () => {
    const nodesOrder1: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '/dev/kafka/local/topics/web_visits' },
      },
      {
        id: 'node-2',
        type: 'geoIp',
        position: { x: 200, y: 0 },
        data: { ipField: 'ip', outputField: 'country_code' },
      },
    ];
    const nodesOrder2: AppNode[] = [
      // Same nodes but reversed order (as might happen when dragging)
      {
        id: 'node-2',
        type: 'geoIp',
        position: { x: 200, y: 0 },
        data: { ipField: 'ip', outputField: 'country_code' },
      },
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 },
        data: { topicPath: '/dev/kafka/local/topics/web_visits' },
      },
    ];
    const edges = [{ source: 'node-1', target: 'node-2' }];

    const key1 = getGraphDependencyKey(nodesOrder1, edges);
    const key2 = getGraphDependencyKey(nodesOrder2, edges);

    // Keys should be identical regardless of array order
    expect(key1).toBe(key2);
  });

  it('should NOT change when node position changes (dragging)', () => {
    const nodesBefore: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 0, y: 0 }, // Original position
        data: { topicPath: '/dev/kafka/local/topics/web_visits' },
      },
    ];
    const nodesAfter: AppNode[] = [
      {
        id: 'node-1',
        type: 'kafkaSource',
        position: { x: 500, y: 300 }, // Dragged to new position
        data: { topicPath: '/dev/kafka/local/topics/web_visits' },
      },
    ];
    const edges: { source: string; target: string }[] = [];

    const keyBefore = getGraphDependencyKey(nodesBefore, edges);
    const keyAfter = getGraphDependencyKey(nodesAfter, edges);

    // Keys should be identical since position is not included
    expect(keyBefore).toBe(keyAfter);
  });
});
