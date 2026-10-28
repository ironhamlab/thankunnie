import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import Dialog from '@/components/common/Dialog';
import Button from '@/components/common/Button';
import '@/assets/styles/home-redesign.css';
import styles from './HomeHeader.module.css';

const NOTIFICATIONS = [
  {
    id: 'booking',
    category: '예약',
    title: '예약이 확정되었어요!',
    text: '살롱 드 르네 · 젤 네일 원컬러 + 케어 예약 내역을 확인해 보세요.',
    time: '방금 전',
    to: ROUTES.BOOKINGS,
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
export default function HomeHeader() {
  const navigate = useNavigate();
  const search = useDesignStore((s) => s.search);
  const setSearch = useDesignStore((s) => s.setSearch);
  const [open, setOpen] = useState(false);
  const [read, setRead] = useState(false);
  const [tab, setTab] = useState('전체');
  const [modelOpen, setModelOpen] = useState(false);
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
  return (
    <>
      <header className={styles.root}>
        <div className={`b-container ${styles.inner}`}>
          <Link to={ROUTES.HOME} className="b-brand">
            땡잡은 언니
          </Link>
          <form className={styles.search} onSubmit={handleSearch}>
            <input
              aria-label="지역, 시술 종류, 매장 검색"
              placeholder="지역, 시술 종류, 매장 검색"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button aria-label="검색">⌕</button>
          </form>
          <nav aria-label="고객 메뉴">
            <Link to={`${ROUTES.HOME}?view=list`}>빈자리 탐색</Link>
            <Link to={`${ROUTES.HOME}?sale=1`}>타임세일</Link>
            <button onClick={() => setModelOpen(true)}>시술 모델 모집</button>
            <Link to={ROUTES.BOOKINGS}>▦ 내 예약</Link>
            <Link to={ROUTES.MY_RESERVATIONS}>예약 내역</Link>
          </nav>
          <div className={styles.actions}>
            <Link to={ROUTES.OWNER} className={styles.account}>
              ● [테스트] 고객 계정
            </Link>
            <div ref={popoverRef}>
              <button
                className={styles.notificationButton}
                aria-label="알림"
                aria-expanded={open}
                aria-controls="home-notifications"
                onClick={() => setOpen(!open)}
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  aria-hidden="true"
                >
                  <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4" />
                </svg>
                {!read && <b>2</b>}
              </button>
              {open && (
                <section
                  id="home-notifications"
                  className="home-notifications"
                  aria-label="실시간 알림"
                >
                  <div className={styles.notificationsTitle}>
                    <strong>♧ 실시간 알림 {read ? 0 : 2}</strong>
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
            <Link to={ROUTES.PROFILE} aria-label="마이페이지">
              ◎
            </Link>
          </div>
        </div>
      </header>
      <nav className={styles.mobileNav} aria-label="모바일 고객 메뉴">
        <Link to={`${ROUTES.HOME}?view=list`}>
          <span>⌖</span>빈자리 탐색
        </Link>
        <Link to={`${ROUTES.HOME}?sale=1`}>
          <span>ϟ</span>타임세일
        </Link>
        <button onClick={() => setModelOpen(true)}>
          <span>✧</span>모델 모집
        </button>
        <Link to={ROUTES.BOOKINGS}>
          <span>▦</span>내 예약
        </Link>
        <Link to={ROUTES.PROFILE}>
          <span>◎</span>마이
        </Link>
      </nav>
      {modelOpen && (
        <Dialog title="시술 모델 모집" onClose={() => setModelOpen(false)}>
          <p>시술 모델 모집 화면은 준비 중입니다. 현재는 메인페이지 UI 시연 환경입니다.</p>
          <Button variant="primary" onClick={() => setModelOpen(false)}>
            확인
          </Button>
        </Dialog>
      )}
    </>
  );
}
