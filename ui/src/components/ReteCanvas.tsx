import { createRoot } from "react-dom/client";
import { useEffect, useState } from "react";
import { NodeEditor, GetSchemes, ClassicPreset } from "rete";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets, ReactArea2D, useRete } from "rete-react-plugin";
import { DockPlugin, DockPresets } from "rete-dock-plugin";
import { useKafkaTopics } from '../services/kafka';
import { streamBetweenTopics } from '../services/topic-stream';

import { ClassicScheme } from "rete-react-plugin";
type Schemes = ClassicScheme;
type AreaExtra = ReactArea2D<Schemes>;

// Node for Kafka topics
class ExistingTopicNode extends ClassicPreset.Node {
  width = 180;
  height = 120;
  topicName: string;

  constructor(name: string, socket: ClassicPreset.Socket) {
    super(name);
    this.topicName = name; // Store topic name for easy access
    this.addControl(
      "name",
      new ClassicPreset.InputControl("text", { initial: name })
    );
    this.addOutput("out", new ClassicPreset.Output(socket));
  }
}

// Node for example topic with input only
class ExampleTopicNode extends ClassicPreset.Node {
  width = 180;
  height = 120;

  constructor(socket: ClassicPreset.Socket) {
    super("exampletopic");
    this.addInput("in", new ClassicPreset.Input(socket));
  }
}

async function createEditor(container: HTMLElement) {
  const socket = new ClassicPreset.Socket("socket");

  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new ReactPlugin<Schemes, AreaExtra>({ createRoot });
  const dock = new DockPlugin<Schemes>();

  // Add dock menu
  dock.addPreset(DockPresets.classic.setup({ area, size: 100, scale: 0.6 }));

  // Add plugins
  editor.use(area);
  area.use(connection);
  area.use(render);
  area.use(dock);

  // Add presets
  render.addPreset(Presets.classic.setup());
  connection.addPreset(ConnectionPresets.classic.setup());

  // Enable node selection
  AreaExtensions.selectableNodes(area, AreaExtensions.selector(), {
    accumulating: AreaExtensions.accumulateOnCtrl()
  });

  return {
    editor,
    area,
    dock,
    socket,
    destroy: () => area.destroy()
  };
}

export function ReteCanvas() {
  const { data: topics = [] } = useKafkaTopics();
  const [ref, editor] = useRete(createEditor);
  const [isStreaming, setIsStreaming] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Add items to dock when editor is ready
  useEffect(() => {
    if (!editor) return;

    // Add existing topics
    topics.forEach(topic => {
      editor.dock.add(() => new ExistingTopicNode(topic, editor.socket));
    });

    // Add example topic node
    editor.dock.add(() => new ExampleTopicNode(editor.socket));
    
    // Add connection listener
    editor.editor.addPipe(context => {
      if (context.type === 'connectioncreate') {
        const connection = context.data;
        handleConnection(connection);
      }
      return context;
    });
  }, [editor, topics]);

  // Handle connection between nodes
  const handleConnection = async (connection: any) => {
    try {
      const sourceNode = editor?.editor.getNode(connection.source);
      const targetNode = editor?.editor.getNode(connection.target);
      
      // Check if connection is from ExistingTopicNode to ExampleTopicNode
      if (sourceNode instanceof ExistingTopicNode && 
          targetNode instanceof ExampleTopicNode) {
        
        // Use the topicName property we added to ExistingTopicNode
        const sourceTopic = (sourceNode as ExistingTopicNode).topicName;
        const targetTopic = 'example_output'; // Default name for example output topic
        
        setIsStreaming(true);
        setError(null);
        setMessage(`Starting stream from ${sourceTopic} to ${targetTopic}...`);
        
        // Start the streaming
        const result = await streamBetweenTopics({
          inputTopic: sourceTopic,
          outputTopic: targetTopic
        });
        
        setMessage(result);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setIsStreaming(false);
    }
  };

  return (
    <div>
      <h2>Kafka Topics</h2>
      <div 
        ref={ref} 
        style={{ 
          width: '100%', 
          height: 'calc(100vh - 250px)', // Reduced height to make room for status
          border: '1px solid #ddd',
          borderRadius: '8px',
          background: '#f5f5f5',
          marginBottom: '20px'
        }}
      />
      
      {/* Status indicators */}
      {isStreaming && (
        <div style={{ 
          padding: '10px', 
          backgroundColor: '#e9f5fe', 
          borderRadius: '4px',
          marginBottom: '10px'
        }}>
          <p style={{ margin: 0 }}>
            <span style={{ 
              display: 'inline-block',
              width: '10px',
              height: '10px',
              borderRadius: '50%',
              backgroundColor: '#0d6efd',
              marginRight: '10px',
              animation: 'pulse 1.5s infinite'
            }}></span>
            <span>Connecting topics...</span>
          </p>
        </div>
      )}

      {message && !error && !isStreaming && (
        <div style={{ 
          padding: '10px', 
          backgroundColor: '#d4edda', 
          borderRadius: '4px',
          marginBottom: '10px',
          color: '#155724'
        }}>
          <p style={{ margin: 0 }}>{message}</p>
        </div>
      )}

      {error && (
        <div style={{ 
          padding: '10px', 
          backgroundColor: '#f8d7da', 
          borderRadius: '4px',
          marginBottom: '10px',
          color: '#721c24'
        }}>
          <p style={{ margin: 0 }}>{error}</p>
        </div>
      )}
      
      {/* Helper text */}
      <div style={{ marginTop: '10px', fontSize: '14px', color: '#666' }}>
        <p>Drag topics from the dock menu to the canvas. Connect an existing topic to the example topic node to start streaming.</p>
      </div>
    </div>
  );
}
