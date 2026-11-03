import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import logo from '@/assets/images/logo.png';
import { ROUTES } from '@/constants/routes';
import { USER_ROLE } from '@/constants/userRole';
import { useCurrentRole } from '@/hooks/useCurrentRole';
import { useInboxStream } from '@/hooks/useInboxStream';
import { useLogout } from '@/hooks/useLogout';
import { useAuthStore } from '@/store/useAuthStore';
import { useDesignStore } from '@/store/useDesignStore';
import { useInboxStore } from '@/store/useInboxStore';
import { getDevTestUser } from '@/utils/devTestUser';
import { formatDateTime } from '@/utils/reservationFormat';
import SearchIcon from '@/components/icons/SearchIcon';
import NotificationIcon from '@/components/icons/NotificationIcon';
import ProfileIcon from '@/components/icons/ProfileIcon';
import TimerIcon from '@/components/icons/TimerIcon';
import CalendarIcon from '@/components/icons/CalendarIcon';
import BoltIcon from '@/components/icons/BoltIcon';
import StoreIcon from '@/components/icons/StoreIcon';
import SproutIcon from '@/components/icons/SproutIcon';
import styles from './Header.module.css';

const INBOX_TABS = [
  { id: 'ALL', label: '전체' },
  { id: 'RESERVATION', label: '예약' },
  { id: 'INTEREST', label: '관심' },
];

const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const wait = (ms) =>
  new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });

/**
 * 지금 주소에 해당하는 메뉴를 고른다. 초보 시술 모집·빈자리 탐색·타임세일은 같은 홈 주소에 붙은 값(?type, ?view, ?sale)으로 구분한다.
 * 초보 시술 모집도 ?view=list 를 쓰므로 빈자리 탐색보다 먼저 본다.
 */
function getActiveMenu({ pathname, search }) {
  const params = new URLSearchParams(search);
  if (pathname === ROUTES.HOME && params.get('type') === 'MODEL') return 'model';
  if (pathname === ROUTES.HOME && params.get('sale') === '1') return 'sale';
  if (pathname === ROUTES.HOME && (params.has('view') || params.has('search'))) return 'search';
  if (pathname === ROUTES.MY_RESERVATIONS) return 'reservations';
  if (pathname === ROUTES.PROFILE) return 'profile';
  return null;
}

function InboxItemBody({ item }) {
  return (
    <>
      <strong>
        {item.readAt == null && <em>● </em>}
        {item.title}
      </strong>
      <p>{item.body}</p>
      <small>{formatDateTime(item.createdAt)}</small>
    </>
  );
}

/**
 * 고객 화면 공통 헤더. 휴대폰 폭에서는 아래 메뉴 막대도 함께 보여준다.
 */
export default function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const activeMenu = getActiveMenu(location);
  const token = useAuthStore((s) => s.token);
  const role = useCurrentRole();
  const logout = useLogout();
  const search = useDesignStore((s) => s.search);
  const setSearch = useDesignStore((s) => s.setSearch);
  const [open, setOpen] = useState(false);
  // 닫을 때 사라지는 움직임이 끝날 때까지 목록 창을 남겨 둔다
  const [leaving, setLeaving] = useState(false);
  const [tab, setTab] = useState('ALL');
  const [isCountIncreased, setIsCountIncreased] = useState(false);
  // 탭 전환·모두 읽음 뒤에 목록이 다시 나타나게 한다
  const [listKey, setListKey] = useState(0);
  const [isClearing, setIsClearing] = useState(false);
  // 개발 서버의 테스트 계정 스위치도 로그인으로 본다. API 토큰과 같은 기준이다.
  const hasSession = Boolean(token) || Boolean(getDevTestUser());
  const isCustomer = hasSession && role === USER_ROLE.CUSTOMER;
  const unreadCount = useInboxStore((state) => state.unreadCount);
  const items = useInboxStore((state) => state.items);
  const listStatus = useInboxStore((state) => state.listStatus);
  const listError = useInboxStore((state) => state.listError);
  const loadInbox = useInboxStore((state) => state.loadInbox);
  const markRead = useInboxStore((state) => state.markRead);
  const markAllRead = useInboxStore((state) => state.markAllRead);
  const previousCountRef = useRef(null);
  const popoverRef = useRef(null);
  const openRef = useRef(open);
  const leavingRef = useRef(leaving);
  openRef.current = open;
  leavingRef.current = leaving;
  useInboxStream(isCustomer, open);
  useEffect(() => {
    const previousCount = previousCountRef.current;
    previousCountRef.current = unreadCount;
    if (previousCount != null && unreadCount > previousCount) {
      setIsCountIncreased(true);
    }
  }, [unreadCount]);

  const closePanel = () => {
    if (!openRef.current || leavingRef.current) {
      return;
    }
    if (reduceMotion()) {
      setOpen(false);
      setLeaving(false);
      return;
    }
    setLeaving(true);
    setOpen(false);
  };

  const handlePanelAnimationEnd = (event) => {
    if (event.target !== event.currentTarget) {
      return;
    }
    if (leavingRef.current && String(event.animationName).includes('panelLeave')) {
      setLeaving(false);
    }
  };

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const handleClick = (event) => {
      if (!popoverRef.current?.contains(event.target)) {
        closePanel();
      }
    };
    const handleKey = (event) => {
      if (event.key === 'Escape') {
        closePanel();
      }
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
  const handleLogout = () => logout(ROUTES.HOME);
  const handleBellClick = () => {
    if (openRef.current || leavingRef.current) {
      closePanel();
      return;
    }
    setLeaving(false);
    setOpen(true);
    if (isCustomer) {
      loadInbox();
    }
  };
  const handleTabChange = (nextTab) => {
    if (nextTab === tab) {
      return;
    }
    setTab(nextTab);
    setListKey((value) => value + 1);
  };
  const handleItemClick = async (item) => {
    if (item.readAt == null) {
      try {
        await markRead(item.inboxId);
      } catch {
        // 읽음 실패해도 이동·닫기는 한다.
      }
    }
    closePanel();
  };

  const handleMarkAllRead = async () => {
    if (!isCustomer || unreadCount === 0 || isClearing) {
      return;
    }
    setIsClearing(true);
    try {
      if (!reduceMotion()) {
        await wait(220);
      }
      await markAllRead();
      await loadInbox();
      setListKey((value) => value + 1);
    } catch {
      // 모두 읽음이 실패하면 뱃지와 목록은 그대로 둔다.
    } finally {
      setIsClearing(false);
    }
  };
  const visibleItems = items.filter((item) => tab === 'ALL' || item.category === tab);
  const showPanel = open || leaving;
  const current = (menu) => (activeMenu === menu ? 'page' : undefined);
  return (
    <>
      <header className={styles.root}>
        <div className={`b-container ${styles.inner}`}>
          <Link to={ROUTES.HOME} className={`b-brand ${styles.brand}`}>
            <img className={styles.logo} src={logo} alt="" />
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
              <SearchIcon size={16} />
            </button>
          </form>

          <nav aria-label="고객 메뉴">
            <Link to={`${ROUTES.HOME}?view=list&date=all`} aria-current={current('search')}>
              <StoreIcon size={16} />
              일반 예약하기
            </Link>
            <Link to={`${ROUTES.HOME}?view=list&type=MODEL`} aria-current={current('model')}>
              <SproutIcon size={16} />
              초보 시술 모집
            </Link>
            <Link to={`${ROUTES.HOME}?sale=1`} aria-current={current('sale')}>
              <TimerIcon size={16} />
              타임세일
            </Link>
            <Link to={ROUTES.MY_RESERVATIONS} aria-current={current('reservations')}>
              <CalendarIcon size={16} /> 내 예약
            </Link>
          </nav>

          <div className={styles.actions}>
            {/* 사장님 센터는 사장님 계정으로 따로 로그인한다. 로그인한 고객에게는 보여주지 않는다 */}
            {!role && (
              <Link to={ROUTES.OWNER} className={styles.ownerLink}>
                사장님 센터
              </Link>
            )}

            {token ? (
              <button
                type="button"
                className={`${styles.loginLink} ${styles.logoutButton}`}
                onClick={handleLogout}
              >
                로그아웃
              </button>
            ) : (
              <Link to={ROUTES.LOGIN} className={styles.loginLink}>
                로그인
              </Link>
            )}

            {/* 알림 영역. 고객만 서버 알림을 본다 */}
            <div className={styles.notificationRoot} ref={popoverRef}>
              <button
                type="button"
                className={styles.notificationButton}
                aria-label={unreadCount > 0 ? `알림, 읽지 않은 알림 ${unreadCount}개` : '알림'}
                aria-expanded={open}
                aria-controls="header-notifications"
                onClick={handleBellClick}
              >
                <NotificationIcon />
                {unreadCount > 0 && (
                  <b
                    className={
                      isCountIncreased ? `${styles.count} ${styles.isIncreased}` : styles.count
                    }
                    onAnimationEnd={(event) => {
                      // CSS Modules 가 키프레임 이름을 바꿔도 countAttend 가 이름에 남는다
                      if (String(event.animationName).includes('countAttend')) {
                        setIsCountIncreased(false);
                      }
                    }}
                  >
                    {unreadCount}
                  </b>
                )}
              </button>
              {showPanel && (
                <section
                  id="header-notifications"
                  className={
                    leaving
                      ? `${styles.notifications} ${styles.isLeaving}`
                      : styles.notifications
                  }
                  aria-label="알림"
                  aria-hidden={leaving || undefined}
                  onAnimationEnd={handlePanelAnimationEnd}
                >
                  <div className={styles.notificationsTitle}>
                    <strong className={isClearing ? styles.isCleared : undefined}>
                      알림 {unreadCount}
                    </strong>
                    <div>
                      <button
                        type="button"
                        onClick={handleMarkAllRead}
                        disabled={!isCustomer || unreadCount === 0 || isClearing || leaving}
                      >
                        모두 읽음
                      </button>
                      <button type="button" aria-label="알림 닫기" onClick={closePanel}>
                        ✕
                      </button>
                    </div>
                  </div>
                  {isCustomer ? (
                    <>
                      <div className={styles.notificationTabs}>
                        {INBOX_TABS.map((item) => (
                          <button
                            key={item.id}
                            type="button"
                            aria-pressed={tab === item.id}
                            onClick={() => handleTabChange(item.id)}
                            disabled={leaving}
                          >
                            {item.label}
                          </button>
                        ))}
                      </div>
                      <div key={`${tab}-${listKey}`} className={styles.notificationList}>
                        {listStatus === 'loading' && visibleItems.length === 0 && (
                          <p className={styles.notificationItem}>불러오는 중입니다.</p>
                        )}
                        {listStatus === 'failed' && (
                          <p className={styles.notificationItem}>{listError}</p>
                        )}
                        {listStatus !== 'loading' &&
                          listStatus !== 'failed' &&
                          visibleItems.length === 0 && (
                            <p className={styles.notificationItem}>알림이 없습니다.</p>
                          )}
                        {visibleItems.map((item) => (
                          <div
                            className={
                              isClearing
                                ? `${styles.notificationItem} ${styles.isClearing}`
                                : styles.notificationItem
                            }
                            key={item.inboxId}
                          >
                            {item.linkPath ? (
                              <Link
                                to={item.linkPath}
                                onClick={() => {
                                  handleItemClick(item);
                                }}
                              >
                                <InboxItemBody item={item} />
                              </Link>
                            ) : (
                              <button
                                type="button"
                                className={styles.notificationItemButton}
                                onClick={() => handleItemClick(item)}
                              >
                                <InboxItemBody item={item} />
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </>
                  ) : (
                    <p className={styles.notificationItem}>
                      {hasSession
                        ? '고객 계정에서 알림을 확인할 수 있습니다.'
                        : '로그인 후 알림을 확인할 수 있습니다.'}
                    </p>
                  )}
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
        <Link to={`${ROUTES.HOME}?view=list&date=all`} aria-current={current('search')}>
          <StoreIcon size={20} />
          일반 예약하기
        </Link>
        <Link to={`${ROUTES.HOME}?view=list&type=MODEL`} aria-current={current('model')}>
          <SproutIcon size={20} />
          초보 시술 모집
        </Link>
        <Link to={`${ROUTES.HOME}?sale=1`} aria-current={current('sale')}>
          <BoltIcon size={20} />
          타임세일
        </Link>
        <Link to={ROUTES.MY_RESERVATIONS} aria-current={current('reservations')}>
          <CalendarIcon size={20} />내 예약
        </Link>
      </nav>
    </>
  );
}
