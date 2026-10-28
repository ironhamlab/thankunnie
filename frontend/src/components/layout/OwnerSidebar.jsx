import { Link, NavLink } from 'react-router-dom';
import Asset from '@/components/common/Asset';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import Button from '@/components/common/Button';
import styles from './OwnerSidebar.module.css';

const NAVIGATION = [
  [ROUTES.OWNER_SHOP, '매장 정보', '12-1798-6.svg'],
  [ROUTES.OWNER_MENUS, '시술 관리', '12-1798-7.svg'],
  [ROUTES.OWNER_SLOTS, '빈자리 관리', '12-1798-8.svg'],
  [ROUTES.OWNER_SCHEDULE, '영업시간·일정', '12-1798-8.svg'],
  [ROUTES.OWNER_BOOKINGS, '예약 관리', '12-1798-9.svg'],
];

/**
 * 사장님 센터 사이드바
 */
export default function OwnerSidebar() {
  const shop = useDesignStore((s) => s.shop);

  return (
    <aside className={styles.root}>
      <Link to={ROUTES.OWNER} className={styles.brand}>
        <strong>땡잡은 언니</strong>
        <small>사장님 센터</small>
      </Link>
      <div className={styles.shop}>
        <Asset name="12-1798-0.png" alt="매장 대표 이미지" />
        <div>
          <strong>{shop.name}</strong>
          <small className="b-green-text">● 영업 중</small>
        </div>
      </div>
      <Button as={Link} variant="primary" wide to={ROUTES.OWNER_SLOT_NEW}>
        ⊕ 빈자리 즉시 등록
      </Button>
      <nav aria-label="사장님 메뉴">
        {NAVIGATION.map(([to, label, icon]) => (
          <NavLink key={to} to={to}>
            <Asset name={icon} />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className={styles.bottom}>
        <NavLink to={ROUTES.OWNER} end>
          마이페이지
        </NavLink>
        <Link to={ROUTES.HOME}>고객 화면으로 전환 →</Link>
        <small>땡잡은 언니 파트너스 · 모의 테스트</small>
      </div>
    </aside>
  );
}
