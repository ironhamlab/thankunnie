import { Link, NavLink } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useShopStore } from '@/store/useShopStore';
import RepresentativeImage from '@/pages/owner/components/RepresentativeImage';
import Button from '@/components/common/Button';
import StoreIcon from '@/components/icons/StoreIcon';
import ScissorsIcon from '@/components/icons/ScissorsIcon';
import CalendarCheckIcon from '@/components/icons/CalendarCheckIcon';
import CalendarIcon from '@/components/icons/CalendarIcon';
import ProfileIcon from '@/components/icons/ProfileIcon';
import styles from './OwnerSidebar.module.css';

const NAVIGATION = [
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
      <Button as={Link} variant="primary" wide to={ROUTES.OWNER_VACANCIES}>
        ⊕ 빈자리 열기·닫기
      </Button>
      <nav aria-label="매장 메뉴">
        {NAVIGATION.map(([to, label, Icon]) => (
          <NavLink key={to} to={to}>
            <Icon size={20} />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className={styles.bottom}>
        <NavLink to={ROUTES.OWNER} end>
          운영 현황
        </NavLink>
      </div>
    </aside>
  );
}
