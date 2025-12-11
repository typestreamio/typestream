import { Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { StreamsPage } from './pages/StreamsPage';
import { CreateJobPage } from './pages/CreateJobPage';
import { StreamDetailPage } from './pages/StreamDetailPage';
import { IntegrationsPage } from './pages/IntegrationsPage';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/streams" replace />} />
      <Route element={<AppLayout />}>
        <Route path="/streams" element={<StreamsPage />} />
        <Route path="/streams/new-job" element={<CreateJobPage />} />
        <Route path="/streams/:jobId" element={<StreamDetailPage />} />
        <Route path="/integrations" element={<IntegrationsPage />} />
      </Route>
    </Routes>
  );
}

export default App;
