import { createRoot } from "react-dom/client";
import { useEffect, useState, useRef } from "react";
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

// Node for new topic with input only
class NewTopicNode extends ClassicPreset.Node {
  width = 180;
  height = 120;
  topicName: string;

  constructor(socket: ClassicPreset.Socket) {
    super("newtopic");
    this.topicName = "newtopic"; // Default name that will be changed via popup
    
    // Add a control to display the topic name
    this.addControl(
      "name",
      new ClassicPreset.InputControl("text", { initial: "newtopic" })
    );
    
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
  const [showPopup, setShowPopup] = useState(false);
  const [activeNode, setActiveNode] = useState<NewTopicNode | null>(null);
  const [newTopicName, setNewTopicName] = useState("");

  // Add items to dock when editor is ready
  useEffect(() => {
    if (!editor) return;

    // Add existing topics
    topics.forEach(topic => {
      editor.dock.add(() => new ExistingTopicNode(topic, editor.socket));
    });

    // Add new topic node
    editor.dock.add(() => new NewTopicNode(editor.socket));
    
    // Add node creation and connection listeners
    editor.editor.addPipe(context => {
      if (context.type === 'connectioncreate') {
        const connection = context.data;
        handleConnection(connection);
      }
      
      // When a node is created, check if it's a NewTopicNode
      if (context.type === 'nodecreated') {
        const node = context.data;
        if (node instanceof NewTopicNode) {
          // Show the popup for naming the topic
          setActiveNode(node);
          setNewTopicName("");
          setShowPopup(true);
        }
      }
      
      return context;
    });
  }, [editor, topics]);

  // Handle connection between nodes
  const handleConnection = async (connection: any) => {
    try {
      const sourceNode = editor?.editor.getNode(connection.source);
      const targetNode = editor?.editor.getNode(connection.target);
      
      // Check if connection is from ExistingTopicNode to NewTopicNode
      if (sourceNode instanceof ExistingTopicNode && 
          targetNode instanceof NewTopicNode) {
        
        // Use the topicName property we added to ExistingTopicNode
        const sourceTopic = (sourceNode as ExistingTopicNode).topicName;
        const targetTopic = (targetNode as NewTopicNode).topicName;
        
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

  // Handle topic name submission
  const handleTopicNameSubmit = () => {
    if (activeNode && newTopicName.trim()) {
      const finalName = newTopicName.trim();
      
      // Update node label and internal topicName property
      activeNode.label = finalName;
      activeNode.topicName = finalName;
      
      // Update the control value to display the new name
      const control = activeNode.controls["name"] as ClassicPreset.InputControl<"text", string>;
      if (control) {
        control.setValue(finalName);
      }
      
      // Reset popup state
      setShowPopup(false);
      setActiveNode(null);
    }
  };
  
  // Handle Enter key press in the input field
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && newTopicName.trim()) {
      handleTopicNameSubmit();
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
          marginBottom: '20px',
          position: 'relative' // For popup positioning
        }}
      />
      
      {/* Topic Name Popup */}
      {showPopup && (
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          backgroundColor: 'white',
          padding: '20px',
          borderRadius: '8px',
          boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
          zIndex: 1000,
          width: '300px',
          textAlign: 'center'
        }}>
          <h3>New Topic</h3>
          <p style={{ marginBottom: '15px' }}>Please enter a name for this topic:</p>
          <input
            type="text"
            value={newTopicName}
            onChange={(e) => setNewTopicName(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Enter topic name"
            style={{
              width: '100%',
              padding: '8px 12px',
              borderRadius: '4px',
              border: '1px solid #ddd',
              marginBottom: '15px',
              fontSize: '14px'
            }}
            autoFocus
          />
          <div style={{ display: 'flex', justifyContent: 'center', gap: '10px' }}>
            <button
              onClick={() => setShowPopup(false)}
              style={{
                padding: '8px 16px',
                backgroundColor: '#f5f5f5',
                border: '1px solid #ddd',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              Cancel
            </button>
            <button
              onClick={handleTopicNameSubmit}
              style={{
                padding: '8px 16px',
                backgroundColor: '#0d6efd',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
              disabled={!newTopicName.trim()}
            >
              Create
            </button>
          </div>
        </div>
      )}
      
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
        <p>Drag topics from the dock menu to the canvas. Connect an existing topic to the new topic node to start streaming.</p>
      </div>
    </div>
  );
}
