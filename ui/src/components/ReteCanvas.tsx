import { createRoot } from "react-dom/client";
import { useEffect } from "react";
import { NodeEditor, GetSchemes, ClassicPreset } from "rete";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets, ReactArea2D, useRete } from "rete-react-plugin";
import { DockPlugin, DockPresets } from "rete-dock-plugin";
import { useKafkaTopics } from '../services/kafka';

import { ClassicScheme } from "rete-react-plugin";
type Schemes = ClassicScheme;
type AreaExtra = ReactArea2D<Schemes>;

// Node for Kafka topics
class ExistingTopicNode extends ClassicPreset.Node {
  width = 180;
  height = 120;

  constructor(name: string, socket: ClassicPreset.Socket) {
    super(name);
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

  // Add items to dock when editor is ready
  useEffect(() => {
    if (!editor) return;

    // Add existing topics
    topics.forEach(topic => {
      editor.dock.add(() => new ExistingTopicNode(topic, editor.socket));
    });

    // Add example topic node
    editor.dock.add(() => new ExampleTopicNode(editor.socket));
  }, [editor, topics]);

  return (
    <div>
      <h2>Kafka Topics</h2>
      <div 
        ref={ref} 
        style={{ 
          width: '100%', 
          height: 'calc(100vh - 150px)',
          border: '1px solid #ddd',
          borderRadius: '8px',
          background: '#f5f5f5'
        }}
      />
    </div>
  );
}
