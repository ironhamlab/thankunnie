import { BrowserRouter } from 'react-router-dom';
import AppRouter from '@/routes/AppRouter';

/**
 * 루트 컴포넌트: Router 조립
 */
export default function App() {
  return (
    <BrowserRouter>
      <AppRouter />
    </BrowserRouter>
  );
}
