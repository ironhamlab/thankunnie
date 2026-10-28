import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import EmptyState from '@/components/common/EmptyState';

/**
 * 존재하지 않는 경로 안내
 */
export default function NotFoundPage() {
  return (
    <EmptyState as="section">
      <h1>페이지를 찾을 수 없습니다.</h1>
      <Link to={ROUTES.HOME}>홈으로 돌아가기</Link>
    </EmptyState>
  );
}
