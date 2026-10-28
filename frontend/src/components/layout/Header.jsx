import { Link, NavLink, useNavigate } from 'react-router-dom';
import Asset from '@/components/common/Asset';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import styles from './Header.module.css';

/**
 * 고객 화면 공통 헤더 (메인 홈 외 화면)
 */
export default function Header() {
  const navigate = useNavigate();
  const search = useDesignStore((s) => s.search);
  const setSearch = useDesignStore((s) => s.setSearch);
  const count = useDesignStore(
    (s) => s.bookings.filter((b) => b.isCustomer && b.status === 'confirmed').length,
  );

  const handleSearch = (event) => {
    event.preventDefault();
    setSearch(search.trim());
    navigate(`${ROUTES.HOME}?search=1`);
  };

  return (
    <header className={styles.root}>
      <div className={`b-container ${styles.nav}`}>
        <Link to={ROUTES.HOME} className="b-brand">
          땡잡은 언니 <span>뷰티 타임세일</span>
        </Link>
        <form className={styles.search} onSubmit={handleSearch}>
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
          <NavLink to={ROUTES.HOME} end>
            빈자리 찾기
          </NavLink>
          <NavLink to={ROUTES.PROFILE}>마이페이지</NavLink>
          <NavLink to={ROUTES.MY_RESERVATIONS}>예약 내역</NavLink>
          <NavLink to={ROUTES.BOOKINGS} className={styles.reservationLink}>
            <Asset name="8-3-16.svg" />내 예약 <b>{count}</b>
          </NavLink>
        </nav>
        <Link className="b-account" to={ROUTES.OWNER}>
          <i />
          [테스트] 고객 계정 <Asset name="8-3-17.svg" />
        </Link>
      </div>
    </header>
  );
}
