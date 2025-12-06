import { useEffect, useState } from 'react';
import { NodeEditor } from 'rete';
import { AreaPlugin, AreaExtensions } from 'rete-area-plugin';
import { ConnectionPlugin, Presets as ConnectionPresets } from 'rete-connection-plugin';
import { ReactPlugin, Presets as ReactPresets } from 'rete-react-plugin';
import { DockPlugin, DockPresets } from 'rete-dock-plugin';
import { createRoot } from 'react-dom/client';
import type { Schemes, AreaExtra } from '../types/rete';
import {
  StreamSourceNode,
  FilterNode,
  MapNode,
  JoinNode,
  GroupNode,
  CountNode,
  EachNode,
  SinkNode,
  NoOpNode
} from '../components/nodes';
import { useKafkaTopics } from './useKafkaTopics';

export function useReteEditor(
  container: HTMLElement | null,
  onNodesChange?: () => void
) {
  const [editor, setEditor] = useState<NodeEditor<Schemes> | null>(null);
  const { data: topicsData } = useKafkaTopics();

  useEffect(() => {
    if (!container) return;

    async function initEditor() {
      console.log('🚀 Initializing Rete editor...');
      console.log('📦 Topics data:', topicsData);

      const editor = new NodeEditor<Schemes>();
      const area = new AreaPlugin<Schemes, AreaExtra>(container!);
      const connection = new ConnectionPlugin<Schemes, AreaExtra>();
      const render = new ReactPlugin<Schemes, AreaExtra>({ createRoot });
      const dock = new DockPlugin<Schemes>();

      console.log('✅ Plugins created');

      // Setup dock with node factories
      dock.addPreset(DockPresets.classic.setup({ area, size: 100, scale: 0.6 }));

      // Enable node selection
      AreaExtensions.selectableNodes(area, AreaExtensions.selector(), {
        accumulating: AreaExtensions.accumulateOnCtrl()
      });

      // Setup rendering
      render.addPreset(ReactPresets.classic.setup() as any);

      // Setup connections
      connection.addPreset(ConnectionPresets.classic.setup() as any);

      // Register plugins
      editor.use(area);
      area.use(connection);
      area.use(render);
      area.use(dock);

      // Add node factories to dock - organized by category

      // Sources: Add a factory for each available Kafka topic
      if (topicsData?.files && topicsData.files.length > 0) {
        console.log(`📝 Adding ${topicsData.files.length} topic factories to dock...`);
        for (const topicName of topicsData.files) {
          const fullPath = `/dev/kafka/local/topics/${topicName}`;
          console.log(`  Adding topic factory: ${topicName}`);
          dock.add(() => new StreamSourceNode(fullPath));
        }
      } else {
        // Fallback: generic StreamSource factory
        console.log('⚠️ No topics data, adding generic StreamSource factory');
        dock.add(() => new StreamSourceNode());
      }

      // Transforms
      dock.add(() => new FilterNode());
      dock.add(() => new MapNode());

      // Aggregations
      dock.add(() => new GroupNode());
      dock.add(() => new CountNode());

      // Joins
      dock.add(() => new JoinNode());

      // Output
      dock.add(() => new EachNode());
      dock.add(() => new SinkNode());

      // Utility
      dock.add(() => new NoOpNode());

      // Simple nodes order
      AreaExtensions.simpleNodesOrder(area);

      // Listen for changes
      if (onNodesChange) {
        editor.addPipe((context) => {
          if (['nodecreated', 'noderemoved', 'connectioncreated', 'connectionremoved'].includes(context.type)) {
            onNodesChange();
          }
          return context;
        });
      }

      // Set editor immediately
      setEditor(editor);

      console.log('🎉 Editor initialization complete!');
    }

    initEditor().catch(err => {
      console.error('❌ Failed to initialize editor:', err);
    });

    return () => {
      console.log('🧹 Cleaning up editor...');
      if (editor) {
        editor.clear().catch(err => console.error('Error clearing editor:', err));
      }
      setEditor(null);
    };
  }, [container, topicsData]);

  return editor;
}
