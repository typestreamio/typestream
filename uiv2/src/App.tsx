import { Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { JobsPage } from './pages/JobsPage';
import { JobDetailPage } from './pages/JobDetailPage';
import { GraphBuilderPage } from './pages/GraphBuilderPage';

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/jobs" replace />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/jobs/new" element={<GraphBuilderPage />} />
        <Route path="/jobs/:jobId" element={<JobDetailPage />} />
      </Route>
    </Routes>
  );
}

export default App;
