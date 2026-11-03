import { useEffect, useState } from 'react';
import { regionNameOf } from '@/constants/regions';
import { useNotificationStore } from '@/store/useNotificationStore';
import { shopLabel, treatmentNameOf } from '@/utils/notificationRules';
import { datePart, timePart } from '@/utils/scheduleDate';
import SubscriptionCreateForm from '@/components/notification/SubscriptionCreateForm';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';

const formatPeriod = (startTime, endTime) =>
  `${datePart(startTime)} ${timePart(startTime)} ~ ${
    datePart(endTime) === datePart(startTime) ? '' : `${datePart(endTime)} `
  }${timePart(endTime)}`;

/**
 * 빈자리 알림 조건 설정. 등록 폼은 구독 페이지와 같은 폼을 쓴다.
 * 아래 목록은 이미 받은 구독이며, 해제만 한다.
 */
export default function SubscriptionSettings() {
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const listError = useNotificationStore((state) => state.listError);
  const categories = useNotificationStore((state) => state.categories);
  const shopNames = useNotificationStore((state) => state.shopNames);
  const shopTreatments = useNotificationStore((state) => state.shopTreatments);
  const saving = useNotificationStore((state) => state.saving);
  const loadShopNames = useNotificationStore((state) => state.loadShopNames);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const [result, setResult] = useState(null);

  const storeShopIds = subscriptions
    .filter((item) => item.subscriptionType === 'STORE')
    .map((item) => Number(item.shopId))
    .filter((id, index, list) => list.indexOf(id) === index)
    .join(',');

  useEffect(() => {
    if (!storeShopIds) return;
    loadShopNames(storeShopIds.split(','));
  }, [storeShopIds, loadShopNames]);

  const categoryNameOf = (categoryId) =>
    categories.find((category) => Number(category.categoryId) === Number(categoryId))?.name ??
    `카테고리 ${categoryId}`;

  const titleOf = (item) =>
    item.subscriptionType === 'STORE'
      ? `매장 알림 · ${shopLabel(item.shopId, shopNames[String(Number(item.shopId))])} · ${treatmentNameOf(
          shopTreatments[String(Number(item.shopId))],
          item.treatmentId,
        )}`
      : `${categoryNameOf(item.categoryId)} · ${regionNameOf(item.regionCode)}`;

  const handleRemove = async (subscriptionId) => {
    const { ok, error } = await removeSubscription(subscriptionId);
    setResult(
      ok
        ? { tone: 'success', message: '알림 조건을 해제했습니다.' }
        : { tone: 'error', message: error.message },
    );
  };

  return (
    <div className="b-stack">
      <SubscriptionCreateForm />

      {result && (
        <Alert as="p" tone={result.tone} role={result.tone === 'error' ? 'alert' : 'status'}>
          {result.message}
        </Alert>
      )}

      {listStatus === 'loading' && <p role="status">알림 조건을 불러오는 중입니다.</p>}
      {listStatus === 'failed' && (
        <Alert as="p" tone="error">
          {listError.message}
        </Alert>
      )}
      {listStatus === 'succeeded' &&
        !subscriptions.some((item) => item.startTime && item.endTime) && (
        <EmptyState className="b-panel">
          <h2>등록된 알림 조건이 없어요</h2>
          <p>원하는 시술·지역·시간을 골라 빈자리 알림을 받아보세요.</p>
        </EmptyState>
      )}
      {listStatus !== 'failed' &&
        subscriptions
          .filter((item) => item.startTime && item.endTime)
          .map((item) => (
          <Panel as="article" padded className="b-row" key={item.subscriptionId}>
            <div>
              <h3>{titleOf(item)}</h3>
              <p className="b-muted">{formatPeriod(item.startTime, item.endTime)}</p>
            </div>
            <Button disabled={saving} onClick={() => handleRemove(item.subscriptionId)}>
              해제
            </Button>
          </Panel>
        ))}
    </div>
  );
}
