import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { useDesignStore } from './useDesignStore';
import Asset from './Asset';

export default function DesignLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const search = useDesignStore((s) => s.search);
  const setSearch = useDesignStore((s) => s.setSearch);
  const count = useDesignStore(
    (s) => s.bookings.filter((b) => b.isCustomer && b.status === 'confirmed').length,
  );
  const [notice, setNotice] = useState('');
  const owner = location.pathname.startsWith(R.OWNER);
  if (owner) return <Outlet />;
  const handleSearch = (event) => {
    event.preventDefault();
    setSearch(search.trim());
    navigate(R.HOME);
  };
  return (
    <div className="b-layout">
      <header className="b-header">
        <div className="b-container b-nav">
          <Link to={R.HOME} className="b-brand">
            땡잡은 언니 <span>뷰티 타임세일</span>
          </Link>
          <form className="b-search" onSubmit={handleSearch}>
            <input
              aria-label="지역, 시술 종류, 매장 검색"
              placeholder="지역, 시술 종류, 매장 검색"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
              }}
            />
            <button aria-label="검색">
              <Asset name="8-3-15.svg" />
            </button>
          </form>
          <nav aria-label="고객 메뉴">
            <NavLink to={R.HOME} end>
              빈자리 탐색
            </NavLink>
            <NavLink to={R.EXPLORE}>타임세일</NavLink>
            <NavLink to={R.BOOKINGS} className="b-reservation-link">
              <Asset name="8-3-16.svg" />내 예약 <b>{count}</b>
            </NavLink>
          </nav>
          <Link className="b-account" to={R.OWNER}>
            <i />
            [테스트] 고객 계정 <Asset name="8-3-17.svg" />
          </Link>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="b-footer">
        <div className="b-container b-row">
          <div>
            <Link className="b-brand" to={R.HOME}>
              땡잡은 언니
            </Link>
            <p>
              © 2025 땡잡은 언니. 본 서비스는 MVP 모의 결제 데모 환경으로 실제 결제가 발생하지
              않습니다.
            </p>
          </div>
          <div className="b-footer-links">
            {['이용약관', '개인정보처리방침', '고객센터', '입점 문의'].map((label) => (
              <button key={label} onClick={() => setNotice(label)}>
                {label}
              </button>
            ))}
            <Link to={R.OWNER}>사장님 센터</Link>
          </div>
        </div>
      </footer>
      {notice && (
        <div className="b-modal-backdrop">
          <section role="dialog" aria-modal="true" aria-label={notice} className="b-panel b-modal">
            <h2>{notice}</h2>
            <p>
              현재는 Figma 디자인 시연 화면입니다. 실제 서비스의 {notice} 내용은 운영 정책 확정 후
              연결됩니다.
            </p>
            <button autoFocus className="b-button b-primary" onClick={() => setNotice('')}>
              닫기
            </button>
          </section>
        </div>
      )}
    </div>
  );
}
