import { useState } from 'react';
import FavoriteShopList from '@/components/notification/FavoriteShopList';
import SmsNotice from '@/components/notification/SmsNotice';
import SubscriptionCreateForm from '@/components/notification/SubscriptionCreateForm';
import SubscriptionList from '@/components/notification/SubscriptionList';
import ClockIcon from '@/components/icons/ClockIcon';
import HeartIcon from '@/components/icons/HeartIcon';
import NotificationIcon from '@/components/icons/NotificationIcon';
import { useNotificationStore } from '@/store/useNotificationStore';
import { isPendingFavorite } from '@/utils/notificationRules';
import headerStyles from '@/components/layout/Header.module.css';
import styles from './MyNotificationsPage.module.css';

const TABS = [
  { id: 'subscriptions', label: '내 알림 구독', icon: ClockIcon },
  { id: 'favorites', label: '알림 전 찜 매장', icon: HeartIcon },
  { id: 'conditions', label: '카테고리·지역 알림 설정', icon: NotificationIcon },
];

/**
 * 구독 화면. 네비의 구독으로 들어오며, 프로필 없이 탭만 보여 준다.
 * 내 알림 구독에서 목록을 보고, 알림 전 찜 매장 줄을 펼쳐 업장 알림 시간을 등록한다.
 * 카테고리·지역 알림 설정에서 시술·지역 구독을 등록한다.
 */
export default function MyNotificationsPage() {
  const [tab, setTab] = useState('subscriptions');
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  // 희망 시간이 없는 업장 찜 수. 0이면 뱃지를 숨긴다
  const pendingFavoriteCount = subscriptions.filter((item) => isPendingFavorite(item)).length;

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <SmsNotice />
      <div className={styles.tabs} role="tablist" aria-label="구독">
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            id={`tab-${id}`}
            type="button"
            role="tab"
            className={styles.tab}
            aria-selected={tab === id}
            aria-controls="notification-tabpanel"
            onClick={() => setTab(id)}
          >
            <Icon size={16} />
            {label}
            {id === 'favorites' && pendingFavoriteCount > 0 && (
              <b className={headerStyles.count} aria-label={`${pendingFavoriteCount}개`}>
                {pendingFavoriteCount}
              </b>
            )}
          </button>
        ))}
      </div>
      <div
        key={tab}
        id="notification-tabpanel"
        className={styles.panel}
        role="tabpanel"
        aria-labelledby={`tab-${tab}`}
      >
        {tab === 'subscriptions' && <SubscriptionList embedded />}
        {tab === 'favorites' && <FavoriteShopList />}
        {tab === 'conditions' && <SubscriptionCreateForm />}
      </div>
    </div>
  );
}
