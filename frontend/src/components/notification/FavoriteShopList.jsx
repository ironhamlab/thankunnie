import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useNotificationStore } from '@/store/useNotificationStore';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import StoreHopeForm from './StoreHopeForm';
import styles from './FavoriteShopList.module.css';

/**
 * 알림 없이 찜만 한 매장. 알림 받기로 조건을 추가하면 이 목록에서 빠진다.
 * 알림 조건이 있는 매장은 내 알림 구독에만 남는다.
 */
export default function FavoriteShopList() {
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const favoriteShops = useNotificationStore((state) => state.favoriteShops);
  const removeFavoriteShop = useNotificationStore((state) => state.removeFavoriteShop);
  const saving = useNotificationStore((state) => state.saving);
  const addSubscription = useNotificationStore((state) => state.addSubscription);
  const shops = favoriteShops.filter(
    (shop) =>
      !subscriptions.some(
        (item) =>
          item.subscriptionType === 'STORE' && Number(item.shopId) === Number(shop.shopId),
      ),
  );
  const [openId, setOpenId] = useState(null);
  const [result, setResult] = useState(null);

  const toggle = (shopId) => {
    setResult(null);
    setOpenId((current) => (current === shopId ? null : shopId));
  };

  const handleSubmit = async (shop, { startTime, endTime }) => {
    const { ok, error } = await addSubscription({
      subscriptionType: 'STORE',
      shopId: Number(shop.shopId),
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
    if (ok) {
      removeFavoriteShop(shop.shopId);
      setOpenId(null);
    }
  };

  if (!shops.length) {
    return (
      <EmptyState>
        <h2>알림 전 찜 매장이 없어요</h2>
        <Link to={ROUTES.HOME}>마음에 드는 매장을 찾아보세요</Link>
      </EmptyState>
    );
  }

  return (
    <div className={styles.list}>
      {result && (
        <Alert as="p" tone={result.tone} role="status">
          {result.message}
        </Alert>
      )}
      {shops.map((shop) => {
        const open = openId === shop.shopId;
        return (
          <Panel as="article" padded className={open ? styles.cardOpen : styles.card} key={shop.shopId}>
            <div className={styles.row}>
              <Link
                className={styles.shopLink}
                to={generatePath(ROUTES.SHOP_DETAIL, { shopId: String(shop.shopId) })}
              >
                <div className={styles.info}>
                  <h2>{shop.shopName || `매장 ${shop.shopId}`}</h2>
                </div>
              </Link>
              <div className={styles.actions}>
                <button
                  type="button"
                  className={styles.favorite}
                  aria-label="찜 해제"
                  aria-pressed="true"
                  onClick={() => removeFavoriteShop(shop.shopId)}
                >
                  ♥
                </button>
                <Button type="button" size="small" aria-expanded={open} onClick={() => toggle(shop.shopId)}>
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
