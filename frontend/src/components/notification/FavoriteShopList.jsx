import { useEffect, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useNotificationStore } from '@/store/useNotificationStore';
import { isPendingFavorite, shopLabel } from '@/utils/notificationRules';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import StoreHopeForm from './StoreHopeForm';
import styles from './FavoriteShopList.module.css';

/**
 * 알림 전 찜 매장. 희망 시간이 없는 업장 구독이다.
 * 알림 받기로 그 구독에 시간을 넣으면 이 목록에서 빠지고, 그때부터 빈자리 알림을 받는다.
 */
export default function FavoriteShopList() {
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const listError = useNotificationStore((state) => state.listError);
  const shopNames = useNotificationStore((state) => state.shopNames);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const loadShopNames = useNotificationStore((state) => state.loadShopNames);
  const updateSubscription = useNotificationStore((state) => state.updateSubscription);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const shops = subscriptions.filter((item) => isPendingFavorite(item));
  const [openId, setOpenId] = useState(null);
  const [result, setResult] = useState(null);

  const storeShopIds = shops
    .map((shop) => Number(shop.shopId))
    .filter((id, index, list) => list.indexOf(id) === index)
    .join(',');

  useEffect(() => {
    loadSubscriptions();
  }, [loadSubscriptions]);

  useEffect(() => {
    if (!storeShopIds) return;
    loadShopNames(storeShopIds.split(','));
  }, [storeShopIds, loadShopNames]);

  const toggle = (shopId) => {
    setResult(null);
    setOpenId((current) => (current === shopId ? null : shopId));
  };

  const handleSubmit = async (shop, { startTime, endTime }) => {
    const { ok, error } = await updateSubscription(shop.subscriptionId, {
      shopId: shop.shopId,
      categoryId: null,
      regionCode: null,
      startTime,
      endTime,
    });
    setResult(
      ok
        ? { tone: 'success', message: '알림 조건을 추가했습니다.' }
        : { tone: 'error', message: error.message },
    );
    if (ok) setOpenId(null);
  };

  const handleRemove = async (subscriptionId) => {
    const { ok, error } = await removeSubscription(subscriptionId);
    setResult(
      ok
        ? { tone: 'success', message: '찜을 해제했습니다.' }
        : { tone: 'error', message: error.message },
    );
  };

  return (
    <div className={styles.list}>
      <Notice>
        희망 시간을 정하기 전에는 문자가 가지 않습니다. 시간을 넣으면 그 매장 빈자리를 가입한 휴대폰
        번호로 알려 줍니다.
      </Notice>
      {listStatus === 'failed' && (
        <Alert tone="error" role="alert">
          {listError?.message ?? '찜한 매장을 불러오지 못했습니다.'}
        </Alert>
      )}
      {(listStatus === 'idle' || listStatus === 'loading') && (
        <p role="status">알림 전 찜 매장을 불러오는 중입니다.</p>
      )}
      {listStatus === 'succeeded' && !shops.length && (
        <EmptyState>
          <h2>알림 전 찜 매장이 없어요</h2>
          <Link to={ROUTES.HOME}>마음에 드는 매장을 찾아보세요</Link>
        </EmptyState>
      )}
      {result && (
        <Alert as="p" tone={result.tone} role={result.tone === 'error' ? 'alert' : 'status'}>
          {result.message}
        </Alert>
      )}
      {listStatus !== 'failed' &&
        shops.map((shop) => {
          const open = openId === shop.shopId;
          return (
            <Panel
              as="article"
              padded
              className={open ? styles.cardOpen : styles.card}
              key={shop.subscriptionId}
            >
              <div className={styles.row}>
                <Link
                  className={styles.shopLink}
                  to={generatePath(ROUTES.SHOP_DETAIL, { shopId: String(shop.shopId) })}
                >
                  <div className={styles.info}>
                    <h2>{shopLabel(shop.shopId, shopNames[String(Number(shop.shopId))])}</h2>
                  </div>
                </Link>
                <div className={styles.actions}>
                  <button
                    type="button"
                    className={styles.favorite}
                    aria-label="찜 해제"
                    aria-pressed="true"
                    disabled={saving}
                    onClick={() => handleRemove(shop.subscriptionId)}
                  >
                    ♥
                  </button>
                  <Button
                    type="button"
                    size="small"
                    aria-expanded={open}
                    onClick={() => toggle(shop.shopId)}
                  >
                    {open ? '접기' : '알림 받기'}
                  </Button>
                </div>
              </div>
              {open && (
                <StoreHopeForm
                  className={styles.expand}
                  saving={saving}
                  onSubmit={(period) => handleSubmit(shop, period)}
                />
              )}
            </Panel>
          );
        })}
    </div>
  );
}
