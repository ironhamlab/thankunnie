import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import NotificationIcon from '@/components/icons/NotificationIcon';
import { useInboxStore } from '@/store/useInboxStore';
import styles from './InboxToastStack.module.css';

function InboxToast({ item, onDismiss }) {
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return undefined;
    }
    const timerId = window.setTimeout(() => onDismiss(item.inboxId), 6200);
    return () => window.clearTimeout(timerId);
  }, [item.inboxId, onDismiss]);

  const close = () => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      onDismiss(item.inboxId);
      return;
    }
    setLeaving(true);
  };

  const handleAnimationEnd = (event) => {
    if (event.target !== event.currentTarget && !event.target.classList.contains(styles.progress)) {
      return;
    }
    const name = String(event.animationName);
    if (name.includes('toastLife') && !leaving) {
      close();
      return;
    }
    if (leaving && name.includes('toastOut')) {
      onDismiss(item.inboxId);
    }
  };

  const body = (
    <>
      <span className={styles.icon} aria-hidden="true">
        <NotificationIcon size={16} />
      </span>
      <span className={styles.copy}>
        <strong>{item.title}</strong>
        <span>{item.body}</span>
      </span>
    </>
  );

  return (
    <article
      className={leaving ? `${styles.toast} ${styles.leaving}` : styles.toast}
      onAnimationEnd={handleAnimationEnd}
    >
      {item.linkPath ? (
        <Link className={styles.link} to={item.linkPath} onClick={close}>
          {body}
        </Link>
      ) : (
        <div className={styles.link}>{body}</div>
      )}
      <button type="button" className={styles.close} aria-label="알림 닫기" onClick={close}>
        ✕
      </button>
      <span className={styles.progress} />
    </article>
  );
}

/**
 * 새로 도착한 받은 알림을 오른쪽 아래에 쌓아 보여 준다.
 * 카드를 누르면 그 알림의 이동 경로로 간다.
 */
export default function InboxToastStack() {
  const toasts = useInboxStore((state) => state.toasts);
  const dismissToast = useInboxStore((state) => state.dismissToast);

  if (toasts.length === 0) {
    return null;
  }

  return (
    <div className={styles.stack} aria-live="polite" aria-label="새 알림">
      {toasts.map((item) => (
        <InboxToast key={item.inboxId} item={item} onDismiss={dismissToast} />
      ))}
    </div>
  );
}
