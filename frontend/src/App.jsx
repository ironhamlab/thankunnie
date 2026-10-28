import { BrowserRouter } from 'react-router-dom';
import DevTestUserSwitch from '@/components/common/DevTestUserSwitch';
import AppRouter from '@/routes/AppRouter';

/**
 * 루트 컴포넌트: Router 조립
 */
export default function App() {
  return (
    <BrowserRouter>
      <AppRouter />
      <DevTestUserSwitch />
    </BrowserRouter>
  );
}
