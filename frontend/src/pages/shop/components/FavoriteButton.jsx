import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useAuthStore } from '@/store/useAuthStore';
import { useNotificationStore } from '@/store/useNotificationStore';
import { getDevTestUser } from '@/utils/devTestUser';
import { addDays, todayKst } from '@/utils/scheduleDate';
import styles from '../ShopDetailPage.module.css';

// 찜은 매장 알림 구독(STORE)이다. 희망 시간을 따로 고르지 않으면 오늘부터 예약 가능 범위(60일)까지 알림을 받는다
const FAVORITE_DAYS = 60;

/** 매장 찜(매장 알림 구독) 등록·해제. 로그인하지 않았으면 로그인 화면으로 보낸다 */
export default function FavoriteButton({ shopId }) {
  const navigate = useNavigate();
  const location = useLocation();
  const token = useAuthStore((state) => state.token);
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const addSubscription = useNotificationStore((state) => state.addSubscription);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const [error, setError] = useState(null);
  // PrivateRoute 와 같이 개발 서버 테스트 계정도 로그인으로 본다
  const loggedIn = Boolean(token || getDevTestUser());

  useEffect(() => {
    if (loggedIn) loadSubscriptions();
  }, [loggedIn, loadSubscriptions]);

  const favorite = loggedIn
    ? subscriptions.find((item) => item.subscriptionType === 'STORE' && item.shopId === shopId)
    : undefined;

  const toggle = async () => {
    if (!loggedIn) {
      navigate(ROUTES.LOGIN, { state: { from: location } });
      return;
    }
    setError(null);
    const today = todayKst();
    const result = favorite
      ? await removeSubscription(favorite.subscriptionId)
      : await addSubscription({
          subscriptionType: 'STORE',
          shopId,
          categoryId: null,
          regionCode: null,
          startTime: `${today}T00:00:00+09:00`,
          endTime: `${addDays(today, FAVORITE_DAYS)}T00:00:00+09:00`,
        });
    if (!result.ok) setError(result.error.message);
  };

  return (
    <div className={styles.favorite}>
      <button
        type="button"
        className={favorite ? 'is-selected' : undefined}
        aria-pressed={Boolean(favorite)}
        aria-label={favorite ? '찜 해제' : '찜하기'}
        disabled={saving || (loggedIn && listStatus === 'loading')}
        onClick={toggle}
      >
        <span aria-hidden="true">{favorite ? '♥' : '♡'}</span>찜
      </button>
      {error && (
        <p role="alert" className={styles.favoriteError}>
          {error}
        </p>
      )}
    </div>
  );
}
