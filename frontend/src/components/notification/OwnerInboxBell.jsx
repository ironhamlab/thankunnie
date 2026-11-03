import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import NotificationIcon from '@/components/icons/NotificationIcon';
import { USER_ROLE } from '@/constants/userRole';
import { useCurrentRole } from '@/hooks/useCurrentRole';
import { useInboxStream } from '@/hooks/useInboxStream';
import { useAuthStore } from '@/store/useAuthStore';
import { useInboxStore } from '@/store/useInboxStore';
import { getDevTestUser } from '@/utils/devTestUser';
import { formatDateTime } from '@/utils/reservationFormat';
import styles from './OwnerInboxBell.module.css';

// 확정·취소는 둘 다 종류가 RESERVATION 이다. 서버가 넣는 제목으로 탭을 나눈다.
const CONFIRMED_TITLE = '예약이 확정되었어요';
const CANCELLED_TITLE = '예약이 취소되었어요';

const OWNER_INBOX_TABS = [
  { id: 'ALL', label: '전체' },
  { id: 'CONFIRMED', label: '예약 완료' },
  { id: 'CANCELLED', label: '취소' },
];

/**
 * 사장님 화면 상단의 알림 종.
 * 고객 종과 같은 뱃지·목록·모두 읽음이다. 관심 탭 대신 예약 완료·취소로 나눈다.
 * 사장님이 로그인했고 탭이 보일 때 알림 스트림에 연결한다.
 */
export default function OwnerInboxBell() {
  const token = useAuthStore((state) => state.token);
  const role = useCurrentRole();
  const hasSession = Boolean(token) || Boolean(getDevTestUser());
  const isOwner = hasSession && role === USER_ROLE.OWNER;
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState('ALL');
  const [isCountIncreased, setIsCountIncreased] = useState(false);
  const unreadCount = useInboxStore((state) => state.unreadCount);
  const items = useInboxStore((state) => state.items);
  const listStatus = useInboxStore((state) => state.listStatus);
  const listError = useInboxStore((state) => state.listError);
  const loadInbox = useInboxStore((state) => state.loadInbox);
  const markAllRead = useInboxStore((state) => state.markAllRead);
  const previousCountRef = useRef(null);
  const popoverRef = useRef(null);
  useInboxStream(isOwner, open);

  useEffect(() => {
    const previousCount = previousCountRef.current;
    previousCountRef.current = unreadCount;
    if (previousCount != null && unreadCount > previousCount) {
      setIsCountIncreased(true);
    }
  }, [unreadCount]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const handleClick = (event) => {
      if (!popoverRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };
    const handleKey = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('pointerdown', handleClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  const handleBellClick = () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (nextOpen && isOwner) {
      loadInbox();
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllRead();
      await loadInbox();
    } catch {
      // 모두 읽음이 실패하면 뱃지와 목록은 그대로 둔다.
    }
  };

  const visibleItems = items.filter((item) => matchesOwnerInboxTab(item, tab));

  return (
    <div className={styles.root} ref={popoverRef}>
      <button
        type="button"
        className={styles.button}
        aria-label={unreadCount > 0 ? `알림, 읽지 않은 알림 ${unreadCount}개` : '알림'}
        aria-expanded={open}
        aria-controls="owner-notifications"
        onClick={handleBellClick}
      >
        <NotificationIcon />
        {unreadCount > 0 && (
          <b
            className={isCountIncreased ? `${styles.count} ${styles.isIncreased}` : styles.count}
            onAnimationEnd={(event) => {
              if (String(event.animationName).includes('countAttend')) {
                setIsCountIncreased(false);
              }
            }}
          >
            {unreadCount}
          </b>
        )}
      </button>
      {open && (
        <section id="owner-notifications" className={styles.panel} aria-label="알림">
          <div className={styles.title}>
            <strong>알림 {unreadCount}</strong>
            <div>
              <button type="button" onClick={handleMarkAllRead} disabled={!isOwner || unreadCount === 0}>
                모두 읽음
              </button>
              <button type="button" aria-label="알림 닫기" onClick={() => setOpen(false)}>
                ✕
              </button>
            </div>
          </div>
          <div className={styles.tabs}>
            {OWNER_INBOX_TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                aria-pressed={tab === item.id}
                onClick={() => setTab(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>
          {listStatus === 'loading' && visibleItems.length === 0 && (
            <p className={styles.item}>불러오는 중입니다.</p>
          )}
          {listStatus === 'failed' && <p className={styles.item}>{listError}</p>}
          {listStatus !== 'loading' && listStatus !== 'failed' && visibleItems.length === 0 && (
            <p className={styles.item}>알림이 없습니다.</p>
          )}
          {visibleItems.map((item) => (
            <div className={styles.item} key={item.inboxId}>
              {item.linkPath ? (
                <Link to={item.linkPath} onClick={() => setOpen(false)}>
                  <InboxItemBody item={item} />
                </Link>
              ) : (
                <InboxItemBody item={item} />
              )}
            </div>
          ))}
        </section>
      )}
    </div>
  );
}

function matchesOwnerInboxTab(item, tab) {
  if (tab === 'CONFIRMED') {
    return item.title === CONFIRMED_TITLE;
  }
  if (tab === 'CANCELLED') {
    return item.title === CANCELLED_TITLE;
  }
  return true;
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
