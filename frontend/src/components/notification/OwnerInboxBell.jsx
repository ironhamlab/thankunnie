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

const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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
  // 닫을 때 사라지는 움직임이 끝날 때까지 목록 창을 남겨 둔다
  const [leaving, setLeaving] = useState(false);
  const [tab, setTab] = useState('ALL');
  const [isCountIncreased, setIsCountIncreased] = useState(false);
  // 탭 전환·모두 읽음 뒤에 목록이 다시 나타나게 한다
  const [listKey, setListKey] = useState(0);
  const [isClearing, setIsClearing] = useState(false);
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
  useInboxStream(isOwner, open);

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

  const handleBellClick = () => {
    if (openRef.current || leavingRef.current) {
      closePanel();
      return;
    }
    setLeaving(false);
    setOpen(true);
    if (isOwner) {
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
    if (!isOwner || unreadCount === 0 || isClearing) {
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

  const visibleItems = items.filter((item) => matchesOwnerInboxTab(item, tab));
  const showPanel = open || leaving;

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
      {showPanel && (
        <section
          id="owner-notifications"
          className={leaving ? `${styles.panel} ${styles.isLeaving}` : styles.panel}
          aria-label="알림"
          aria-hidden={leaving || undefined}
          onAnimationEnd={handlePanelAnimationEnd}
        >
          <div className={styles.title}>
            <strong className={isClearing ? styles.isCleared : undefined}>알림 {unreadCount}</strong>
            <div>
              <button
                type="button"
                onClick={handleMarkAllRead}
                disabled={!isOwner || unreadCount === 0 || isClearing || leaving}
              >
                모두 읽음
              </button>
              <button type="button" aria-label="알림 닫기" onClick={closePanel}>
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
                onClick={() => handleTabChange(item.id)}
                disabled={leaving}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div key={`${tab}-${listKey}`} className={styles.list}>
            {listStatus === 'loading' && visibleItems.length === 0 && (
              <p className={styles.item}>불러오는 중입니다.</p>
            )}
            {listStatus === 'failed' && <p className={styles.item}>{listError}</p>}
            {listStatus !== 'loading' && listStatus !== 'failed' && visibleItems.length === 0 && (
              <p className={styles.item}>알림이 없습니다.</p>
            )}
            {visibleItems.map((item) => (
              <div
                className={isClearing ? `${styles.item} ${styles.isClearing}` : styles.item}
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
                  <button type="button" className={styles.itemButton} onClick={() => handleItemClick(item)}>
                    <InboxItemBody item={item} />
                  </button>
                )}
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function wait(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
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
