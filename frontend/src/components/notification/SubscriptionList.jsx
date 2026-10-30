import { useEffect, useState } from 'react';
import {
  EXAMPLE_CATEGORIES,
  EXAMPLE_SUBSCRIPTIONS,
  shopTitleOf,
} from '@/constants/notificationExamples';
import { regionNameOf } from '@/constants/regions';
import { useNotificationStore } from '@/store/useNotificationStore';
import { datePart, timePart } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import ClockIcon from '@/components/icons/ClockIcon';
import InfoIcon from '@/components/icons/InfoIcon';
import SubscriptionEditDialog from './SubscriptionEditDialog';
import styles from './SubscriptionList.module.css';

const monthDay = (dateTime) => {
  const [, month, day] = datePart(dateTime).split('-');
  return `${month}/${day}`;
};

const formatHope = (startTime, endTime) =>
  `${monthDay(startTime)} ${timePart(startTime)} ~ ${monthDay(endTime)} ${timePart(endTime)}`;

/**
 * 내 알림 구독 목록. 해제하지 않은 구독만 최근 등록 순으로 보여주고, 조건 수정과 해제를 한다.
 * 서버 목록이 비었거나 불러오지 못하면 업장·카테고리·지역 예시 카드를 보여 준다.
 *
 * @param {boolean} [embedded] 구독 화면 탭 안에 넣을 때 바깥 여백을 빼기 위해 사용한다
 */
export default function SubscriptionList({ embedded = false }) {
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const listError = useNotificationStore((state) => state.listError);
  const categories = useNotificationStore((state) => state.categories);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const loadCategories = useNotificationStore((state) => state.loadCategories);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const [examples, setExamples] = useState(EXAMPLE_SUBSCRIPTIONS);
  const [editing, setEditing] = useState(null);
  const [result, setResult] = useState(null);

  useEffect(() => {
    loadSubscriptions();
    loadCategories();
  }, [loadSubscriptions, loadCategories]);

  const useExamples =
    listStatus === 'failed' || (listStatus === 'succeeded' && subscriptions.length === 0);
  const items = useExamples ? examples : subscriptions;
  const categoryOptions = categories.length ? categories : EXAMPLE_CATEGORIES;

  const categoryNameOf = (categoryId) =>
    categoryOptions.find((category) => category.categoryId === categoryId)?.name ??
    `카테고리 ${categoryId}`;

  const titleOf = (item) =>
    item.subscriptionType === 'STORE'
      ? shopTitleOf(item)
      : `${categoryNameOf(item.categoryId)} · ${regionNameOf(item.regionCode)}`;

  const handleRemove = async (subscriptionId) => {
    if (useExamples) {
      setExamples((prev) => prev.filter((item) => item.subscriptionId !== subscriptionId));
      setResult({ tone: 'success', message: '구독을 해제했습니다.' });
      return;
    }
    const { ok, error } = await removeSubscription(subscriptionId);
    setResult(
      ok
        ? { tone: 'success', message: '구독을 해제했습니다.' }
        : { tone: 'error', message: error.message },
    );
  };

  const handleExampleSaved = (subscriptionId, request) => {
    setExamples((prev) =>
      prev.map((item) => {
        if (item.subscriptionId !== subscriptionId) return item;
        return {
          ...item,
          shopId: request.shopId,
          categoryId: request.categoryId,
          regionCode: request.regionCode,
          startTime: request.startTime,
          endTime: request.endTime,
          shopName:
            item.subscriptionType === 'STORE'
              ? shopTitleOf({ ...item, shopId: request.shopId })
              : null,
        };
      }),
    );
    setResult({ tone: 'success', message: '알림 조건을 수정했습니다.' });
  };

  return (
    <div className={embedded ? styles.embedded : `b-container b-page ${styles.root}`}>
      <header className={styles.intro}>
        <h1>내 알림 구독</h1>
        <p className={styles.lead}>해제하지 않은 구독만 최근 등록 순으로 보여요.</p>
      </header>

      {useExamples && listStatus !== 'loading' && (
        <Notice className={styles.demoNotice}>
          {listStatus === 'failed'
            ? `${listError?.message ?? '서버에 연결할 수 없습니다.'} 업장·카테고리·지역 예시 구독을 보여 줍니다.`
            : '등록된 구독이 없어 업장·카테고리·지역 예시 구독을 보여 줍니다.'}
        </Notice>
      )}

      {result && (
        <Alert as="p" tone={result.tone} role="status">
          {result.message}
        </Alert>
      )}
      {listStatus === 'loading' && <p role="status">알림 구독을 불러오는 중입니다.</p>}
      {listStatus !== 'loading' && !items.length && (
        <EmptyState>
          <h2>등록된 알림 구독이 없어요</h2>
          <p>시술·지역이나 매장의 희망 시간을 정하면 빈자리 문자를 받을 수 있어요.</p>
        </EmptyState>
      )}

      <div className={styles.list}>
        {items.map((item) => {
          const isStore = item.subscriptionType === 'STORE';
          return (
            <Panel as="article" padded className={styles.card} key={item.subscriptionId}>
              <div className={styles.head}>
                <Badge className={isStore ? styles.storeBadge : styles.categoryBadge}>
                  {isStore ? '업장' : '카테고리·지역'}
                </Badge>
                <h2 className={styles.title}>{titleOf(item)}</h2>
                <Badge tone="success">알림 받는 중</Badge>
              </div>
              <p className={styles.period}>
                <ClockIcon size={16} />
                희망 시간 {formatHope(item.startTime, item.endTime)}
              </p>
              <div className={styles.actions}>
                <Button
                  type="button"
                  size="small"
                  className={styles.editButton}
                  disabled={saving}
                  onClick={() => setEditing(item)}
                >
                  조건 수정
                </Button>
                <Button
                  type="button"
                  variant="primary"
                  size="small"
                  disabled={saving}
                  onClick={() => handleRemove(item.subscriptionId)}
                >
                  구독 해제
                </Button>
              </div>
            </Panel>
          );
        })}
      </div>

      {items.length > 0 && (
        <p className={styles.note}>
          <InfoIcon size={16} />
          업장 구독은 희망 시간만, 카테고리·지역 구독은 시술·지역과 희망 시간만 수정됩니다.
        </p>
      )}

      {editing && (
        <SubscriptionEditDialog
          subscription={editing}
          categories={categoryOptions}
          onClose={() => setEditing(null)}
          onSaved={
            useExamples
              ? (request) => handleExampleSaved(editing.subscriptionId, request)
              : () => setResult({ tone: 'success', message: '알림 조건을 수정했습니다.' })
          }
          localOnly={useExamples}
        />
      )}
    </div>
  );
}
