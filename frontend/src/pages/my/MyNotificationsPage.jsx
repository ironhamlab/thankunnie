import { useState } from 'react';
import Panel from '@/components/common/Panel';
import FavoriteShopList from '@/components/notification/FavoriteShopList';
import SmsNotice from '@/components/notification/SmsNotice';
import SubscriptionCreateForm from '@/components/notification/SubscriptionCreateForm';
import SubscriptionList from '@/components/notification/SubscriptionList';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useTermsStore } from '@/store/useTermsStore';
import { isPendingFavorite } from '@/utils/notificationRules';
import styles from './MyNotificationsPage.module.css';

const TABS = [
  { id: 'subscriptions', label: '내 알림 찾기' },
  { id: 'favorites', label: '알림 전 찜 매장' },
  { id: 'conditions', label: '카테고리·지역 알림 설정' },
];

/**
 * 마이페이지 알림 구독. 별도 주소는 없고 프로필 화면 안에서만 보여 준다.
 * 내 알림 구독에서 목록을 보고, 알림 전 찜 매장 줄을 펼쳐 업장 알림 시간을 등록한다.
 * 카테고리·지역 알림 설정에서 시술·지역 구독을 등록한다.
 * 동의 확인·저장이 끝나는 동안에는 탭을 바꾸지 못하게 한다.
 */
export default function MyNotificationsPage() {
  const [tab, setTab] = useState('subscriptions');
  // 자식 폼의 동의 확인 중. 저장 중·약관 모달은 스토어로 본다
  const [actionLocked, setActionLocked] = useState(false);
  const saving = useNotificationStore((state) => state.saving);
  const termsPending = useTermsStore((state) => state.pending);
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const tabsLocked = actionLocked || saving || Boolean(termsPending);
  // 희망 시간이 없는 업장 찜 수. 0이면 뱃지를 숨긴다
  const pendingFavoriteCount = subscriptions.filter((item) => isPendingFavorite(item)).length;

  return (
    <div className={styles.root}>
      <SmsNotice />
      <Panel as="nav" className={styles.tabs} role="tablist" aria-label="알림">
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            id={`tab-${id}`}
            type="button"
            role="tab"
            className={styles.tab}
            aria-selected={tab === id}
            aria-controls="notification-tabpanel"
            disabled={tabsLocked}
            onClick={() => setTab(id)}
          >
            {label}
            {id === 'favorites' && pendingFavoriteCount > 0 && (
              <b className={styles.count} aria-label={`${pendingFavoriteCount}개`}>
                {pendingFavoriteCount}
              </b>
            )}
          </button>
        ))}
      </Panel>
      <div
        key={tab}
        id="notification-tabpanel"
        className={styles.panel}
        role="tabpanel"
        aria-labelledby={`tab-${tab}`}
      >
        {tab === 'subscriptions' && <SubscriptionList embedded />}
        {tab === 'favorites' && <FavoriteShopList onBusyChange={setActionLocked} />}
        {tab === 'conditions' && <SubscriptionCreateForm onBusyChange={setActionLocked} />}
      </div>
    </div>
  );
}
