import { Link, NavLink, useLocation } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useSlidingIndicator } from '@/hooks/useSlidingIndicator';
import { useAuthStore } from '@/store/useAuthStore';
import { useShopStore } from '@/store/useShopStore';
import RepresentativeImage from '@/components/common/RepresentativeImage';
import Button from '@/components/common/Button';
import SlidingIndicator from '@/components/common/SlidingIndicator';
import ChartIcon from '@/components/icons/ChartIcon';
import StoreIcon from '@/components/icons/StoreIcon';
import ScissorsIcon from '@/components/icons/ScissorsIcon';
import CalendarCheckIcon from '@/components/icons/CalendarCheckIcon';
import CalendarIcon from '@/components/icons/CalendarIcon';
import ProfileIcon from '@/components/icons/ProfileIcon';
import styles from './OwnerSidebar.module.css';

// 네 번째 값이 true 면 주소가 정확히 같을 때만 고른 것으로 본다. 운영 현황 주소는 다른 메뉴 주소의 앞부분이다
const NAVIGATION = [
  [ROUTES.OWNER, '운영 현황', ChartIcon, true],
  [ROUTES.OWNER_SHOP, '매장 정보', StoreIcon],
  [ROUTES.OWNER_MENUS, '시술 관리', ScissorsIcon],
  [ROUTES.OWNER_SCHEDULE, '영업시간', CalendarCheckIcon],
  [ROUTES.OWNER_BOOKINGS, '예약 관리', CalendarIcon],
  [ROUTES.OWNER_CUSTOMERS, '고객 관리', ProfileIcon],
];

/**
 * 매장 센터 사이드바
 */
export default function OwnerSidebar() {
  const shop = useShopStore((s) => s.shop);
  const shopStatus = useShopStore((s) => s.status);
  const token = useAuthStore((s) => s.token);
  const { pathname } = useLocation();
  // 취소 화면처럼 메뉴 아래 주소에서도 그 메뉴를 고른 것으로 본다
  const activePath = NAVIGATION.find(
    ([to, , , end]) => pathname === to || (!end && pathname.startsWith(`${to}/`)),
  )?.[0];
  const indicator = useSlidingIndicator(activePath);

  return (
    <aside className={styles.root}>
      <Link to={ROUTES.OWNER} className={styles.brand}>
        <strong>땡잡은 언니</strong>
        <small>매장 센터</small>
      </Link>
      {shop && (
        <div className={styles.shop}>
          <RepresentativeImage kind="shop" imageUrl={shop.imageUrl} name={shop.name} />
          <div>
            <strong>{shop.name}</strong>
          </div>
        </div>
      )}
      {/* 첫 접속에 카드가 늦게 와도 아래 메뉴가 밀려 내려가지 않게 자리를 잡아 둔다 */}
      {!shop && shopStatus !== 'failed' && (token || shopStatus === 'loading') && (
        <div className={`${styles.shop} ${styles.shopPlaceholder}`} aria-hidden="true" />
      )}
      <Button as={Link} variant="primary" wide to={ROUTES.OWNER_VACANCIES}>
        <span className={styles.plus} aria-hidden="true">
          ⊕
        </span>{' '}
        빈자리 열기·닫기
      </Button>
      <nav aria-label="매장 메뉴">
        <SlidingIndicator indicator={indicator} className={styles.navIndicator} />
        {NAVIGATION.map(([to, label, Icon, end]) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            ref={activePath === to ? indicator.selectedRef : null}
          >
            <Icon size={20} />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
