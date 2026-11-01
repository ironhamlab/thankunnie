import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import DevTestUserSwitch from '@/components/common/DevTestUserSwitch';
import TermsAgreementDialog from '@/components/common/TermsAgreementDialog';
import AppRouter from '@/routes/AppRouter';

// 결제 화면을 떠날 때 확인창을 띄우는 useBlocker 가 data router 에서만 동작해 createBrowserRouter 를 쓴다.
// 실제 경로는 AppRouter 의 <Routes> 가 그대로 맡는다.
const router = createBrowserRouter([
  {
    path: '*',
    element: (
      <>
        <AppRouter />
        <DevTestUserSwitch />
        {/* 구독 등록·초보 시술 예약이 약관 미동의로 막히면 뜨는 동의 모달 (useTermsStore) */}
        <TermsAgreementDialog />
      </>
    ),
  },
]);

/**
 * 루트 컴포넌트: Router 조립
 */
export default function App() {
  return <RouterProvider router={router} />;
}
