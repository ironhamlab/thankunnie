import MainPage from '../main/MainPage.jsx';
import { useMainStore } from '../../store/useMainStore.js';

/** C 탐색 API 기반 메인 노출 화면. 탐색·상세 라우트는 도메인 확정 후 연결한다. */
export default function HomePage() {
  return <MainPage useMainStore={useMainStore} />;
}
