import { Link } from 'react-router-dom';
import { USER_ROLE } from '@/constants/userRole';
import { useCurrentRole } from '@/hooks/useCurrentRole';
import { getHomePathByRole } from '@/utils/authRedirect';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';

const HOME_LABEL = {
  [USER_ROLE.OWNER]: '매장 센터로 가기',
  [USER_ROLE.ADMIN]: '관리자 화면으로 가기',
};

/**
 * 존재하지 않는 경로 안내. 고객 화면·매장 센터·관리자 화면이 함께 쓴다.
 * 주소를 잘못 입력한 경우가 대부분이므로 오류처럼 보이지 않게 안내하고, 역할별 첫 화면으로 돌아갈 길을 준다.
 */
export default function NotFoundPage() {
  const role = useCurrentRole();

  return (
    <EmptyState as="section" className="b-container">
      <h1>주소를 다시 확인해 주세요</h1>
      <p>찾으시는 화면이 없거나 주소가 바뀌었어요.</p>
      <div className="b-flex b-center">
        <Button as={Link} variant="primary" to={getHomePathByRole(role)} replace>
          {HOME_LABEL[role] ?? '홈으로 가기'}
        </Button>
      </div>
    </EmptyState>
  );
}
