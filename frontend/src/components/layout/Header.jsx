import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import Asset from '@/components/common/Asset';
import NotificationIcon from '@/components/icons/NotificationIcon';
import ProfileIcon from '@/components/icons/ProfileIcon';
import styles from './Header.module.css';

const NOTIFICATIONS = [
  {
    id: 'booking',
    category: '예약',
    title: '예약이 확정되었어요!',
    text: '살롱 드 르네 · 젤 네일 원컬러 + 케어 예약 내역을 확인해 보세요.',
    time: '방금 전',
    to: ROUTES.MY_RESERVATIONS,
  },
  {
    id: 'sale',
    category: '타임세일',
    title: '관심 지역에 새로운 빈자리가 열렸어요',
    text: '놓치기 아까운 오늘의 뷰티 타임세일을 확인해 보세요.',
    time: '5분 전',
    to: `${ROUTES.HOME}?sale=1`,
  },
];

/**
 * 지금 주소에 해당하는 메뉴를 고른다. 빈자리 탐색·타임세일은 같은 홈 주소에 붙은 값(?view, ?sale)으로 구분한다.
 */
function getActiveMenu({ pathname, search }) {
  const params = new URLSearchParams(search);
  if (pathname === ROUTES.HOME && params.get('sale') === '1') return 'sale';
  if (pathname === ROUTES.HOME && (params.has('view') || params.has('search'))) return 'search';
  if (pathname.startsWith(ROUTES.MY_RESERVATIONS)) return 'reservations';
  if (pathname === ROUTES.PROFILE) return 'profile';
  return null;
}

/**
 * 고객 화면 공통 헤더. 휴대폰 폭에서는 아래 메뉴 막대도 함께 보여준다.
 */
export default function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const activeMenu = getActiveMenu(location);
  const search = useDesignStore((s) => s.search);
  const setSearch = useDesignStore((s) => s.setSearch);
  const [open, setOpen] = useState(false);
  const [read, setRead] = useState(false);
  const [tab, setTab] = useState('전체');
  const unreadCount = read ? 0 : NOTIFICATIONS.length;
  const popoverRef = useRef(null);
  useEffect(() => {
    if (!open) return;
    const handleClick = (event) => {
      if (!popoverRef.current?.contains(event.target)) setOpen(false);
    };
    const handleKey = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('pointerdown', handleClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);
  const handleSearch = (event) => {
    event.preventDefault();
    setSearch(search.trim());
    navigate(`${ROUTES.HOME}?search=1`);
  };
  const current = (menu) => (activeMenu === menu ? 'page' : undefined);
  return (
    <>
      <header className={styles.root}>
        <div className={`b-container ${styles.inner}`}>
          <Link to={ROUTES.HOME} className="b-brand">
            땡잡은 언니
          </Link>

          {/* 검색 폼 */}
          <form className={styles.search} onSubmit={handleSearch}>
            <input
              aria-label="지역, 시술 종류, 매장 검색"
              placeholder="지역, 시술 종류, 매장 검색"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button aria-label="검색">
              <Asset name="8-3-15.svg" />
            </button>
          </form>

          <nav aria-label="고객 메뉴">
            <Link to={`${ROUTES.HOME}?view=list`} aria-current={current('search')}>
              빈자리 탐색
            </Link>
            <Link to={`${ROUTES.HOME}?sale=1`} aria-current={current('sale')}>
              타임세일
            </Link>
            <Link to={ROUTES.MY_RESERVATIONS} aria-current={current('reservations')}>
              내 예약
            </Link>
          </nav>

          <div className={styles.actions}>
            <Link to={ROUTES.OWNER} className={styles.ownerLink}>
              사장님 센터
            </Link>

            <Link to={ROUTES.LOGIN} className={styles.loginLink}>
              로그인
            </Link>

            {/* 알림 영역 */}
            <div ref={popoverRef}>
              <button
                className={styles.notificationButton}
                aria-label={unreadCount > 0 ? `알림, 읽지 않은 알림 ${unreadCount}개` : '알림'}
                aria-expanded={open}
                aria-controls="header-notifications"
                onClick={() => setOpen(!open)}
              >
                <NotificationIcon />
                {unreadCount > 0 && <b>{unreadCount}</b>}
              </button>
              {open && (
                <section
                  id="header-notifications"
                  className={styles.notifications}
                  aria-label="실시간 알림"
                >
                  <div className={styles.notificationsTitle}>
                    <strong>♧ 실시간 알림 {unreadCount}</strong>
                    <div>
                      <button onClick={() => setRead(true)}>
                        {read ? '읽음 완료' : '모두 읽음'}
                      </button>
                      <button aria-label="알림 닫기" onClick={() => setOpen(false)}>
                        ✕
                      </button>
                    </div>
                  </div>
                  <div className={styles.notificationTabs}>
                    {['전체', '예약', '타임세일'].map((label) => (
                      <button
                        key={label}
                        aria-pressed={tab === label}
                        onClick={() => setTab(label)}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                  {NOTIFICATIONS.filter((n) => tab === '전체' || n.category === tab).map((n) => (
                    <div className={styles.notificationItem} key={n.id}>
                      <Link to={n.to} onClick={() => setOpen(false)}>
                        <strong>
                          {!read && <em>● </em>}
                          {n.title}
                        </strong>
                        <p>{n.text}</p>
                        <small>{n.time} · 예시 알림</small>
                      </Link>
                    </div>
                  ))}
                </section>
              )}
            </div>

            <Link
              to={ROUTES.PROFILE}
              className={styles.profileLink}
              aria-label="마이페이지"
              aria-current={current('profile')}
            >
              <ProfileIcon />
            </Link>
          </div>
        </div>
      </header>

      <nav className={styles.mobileNav} aria-label="모바일 고객 메뉴">
        <Link to={`${ROUTES.HOME}?view=list`} aria-current={current('search')}>
          <span aria-hidden="true">⌖</span>빈자리 탐색
        </Link>
        <Link to={`${ROUTES.HOME}?sale=1`} aria-current={current('sale')}>
          <span aria-hidden="true">ϟ</span>타임세일
        </Link>
        <Link to={ROUTES.MY_RESERVATIONS} aria-current={current('reservations')}>
          <span aria-hidden="true">▦</span>내 예약
        </Link>
      </nav>
    </>
  );
}
