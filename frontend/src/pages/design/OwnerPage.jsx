import { Link, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { useDesignStore } from './useDesignStore';
import OwnerDashboard from './OwnerDashboard';
import ShopPage from './ShopPage';
import MenusPage from './MenusPage';
import SlotsPage from './SlotsPage';
import OwnerBookingsPage from './OwnerBookingsPage';
import Asset from './Asset';

const navigation = [
  [R.SHOP, '매장 정보', '12-1798-6.svg'],
  [R.MENUS, '시술 관리', '12-1798-7.svg'],
  [R.SLOTS, '빈자리 관리', '12-1798-8.svg'],
  [R.OWNER_BOOKINGS, '예약 관리', '12-1798-9.svg'],
];
export default function OwnerPage() {
  const shop = useDesignStore((s) => s.shop);
  const location = useLocation();
  return (
    <div className="b-owner">
      <aside className="b-sidebar">
        <Link to={R.OWNER} className="b-owner-brand">
          <strong>땡잡은 언니</strong>
          <small>사장님 센터</small>
        </Link>
        <div className="b-owner-shop">
          <Asset name="12-1798-0.png" alt="매장 대표 이미지" />
          <div>
            <strong>{shop.name}</strong>
            <small className="b-green-text">● 영업 중</small>
          </div>
        </div>
        <Link className="b-button b-primary b-wide" to={R.SLOTS + '?new=1'}>
          ⊕ 빈자리 즉시 등록
        </Link>
        <nav aria-label="사장님 메뉴">
          {navigation.map(([to, label, icon]) => (
            <NavLink key={to} to={to}>
              <Asset name={icon} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="b-sidebar-bottom">
          <NavLink to={R.OWNER} end>
            마이페이지
          </NavLink>
          <Link to={R.HOME}>고객 화면으로 전환 →</Link>
          <small>땡잡은 언니 파트너스 · 모의 테스트</small>
        </div>
      </aside>
      <div className="b-owner-body">
        <header className="b-owner-header">
          <div className="b-flex">
            <strong>{shop.name}</strong>
            <span className="b-badge">파트너 가맹점</span>
          </div>
          <div className="b-flex">
            <Link className="b-button b-small" to={R.HOME}>
              ↔ 고객 모드로 전환
            </Link>
            <span className="b-account">● [테스트] 사장님 계정 (살롱 드 르네)</span>
          </div>
        </header>
        <main className="b-owner-main" key={location.pathname}>
          <Routes>
            <Route index element={<OwnerDashboard />} />
            <Route path="shop" element={<ShopPage />} />
            <Route path="menus" element={<MenusPage />} />
            <Route path="slots" element={<SlotsPage />} />
            <Route path="bookings" element={<OwnerBookingsPage />} />
            <Route path="*" element={<div className="b-empty">화면을 찾을 수 없습니다.</div>} />
          </Routes>
        </main>
      </div>
    </div>
  );
}
