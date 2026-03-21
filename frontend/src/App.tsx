import { Routes, Route } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import BoardView from './pages/BoardView';
import { ThemeProvider } from './contexts/ThemeContext';

function App() {
  return (
    <ThemeProvider>
      <div className="app-container">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/board/:id" element={<BoardView />} />
        </Routes>
      </div>
    </ThemeProvider>
  );
}

export default App;
