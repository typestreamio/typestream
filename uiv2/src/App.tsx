import { Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { JobsPage } from './pages/JobsPage';
import { JobDetailPage } from './pages/JobDetailPage';
import { GraphBuilderPage } from './pages/GraphBuilderPage';
import { ConnectionsPage } from './pages/ConnectionsPage';
import { ConnectionCreatePage } from './pages/ConnectionCreatePage';
import { ConnectorsPage } from './pages/ConnectorsPage';
import { ConnectorCreatePage } from './pages/ConnectorCreatePage';

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/jobs" replace />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/jobs/new" element={<GraphBuilderPage />} />
        <Route path="/jobs/:jobId" element={<JobDetailPage />} />
        <Route path="/connections" element={<ConnectionsPage />} />
        <Route path="/connections/new" element={<ConnectionCreatePage />} />
        {/* Kafka Connect connectors - for debugging */}
        <Route path="/connectors" element={<ConnectorsPage />} />
        <Route path="/connectors/new" element={<ConnectorCreatePage />} />
      </Route>
    </Routes>
  );
}

export default App;
