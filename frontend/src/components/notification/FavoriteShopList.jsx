import { useEffect, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { TERMS_CODE } from '@/constants/terms';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useTermsStore } from '@/store/useTermsStore';
import { isPendingFavorite, selectableTreatments, shopLabel, treatmentNameOf } from '@/utils/notificationRules';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import StoreHopeForm from './StoreHopeForm';
import styles from './FavoriteShopList.module.css';

/**
 * 알림 전 찜 매장. 희망 시간이 없는 업장 구독이다.
 * 알림 받기는 문자 수신 동의가 있을 때만 시간 입력을 연다. 없으면 동의 모달을 띄운다.
 * 시간을 저장하면 이 목록에서 빠지고, 그때부터 빈자리 알림을 받는다.
 */
export default function FavoriteShopList({ onBusyChange }) {
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const listError = useNotificationStore((state) => state.listError);
  const shopNames = useNotificationStore((state) => state.shopNames);
  const shopTreatments = useNotificationStore((state) => state.shopTreatments);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const loadShopNames = useNotificationStore((state) => state.loadShopNames);
  const updateSubscription = useNotificationStore((state) => state.updateSubscription);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const ensureAgreement = useTermsStore((state) => state.ensureAgreement);
  const shops = subscriptions.filter((item) => isPendingFavorite(item));
  const [openId, setOpenId] = useState(null);
  const [treatmentId, setTreatmentId] = useState('');
  const [agreeing, setAgreeing] = useState(false);
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

  useEffect(() => () => onBusyChange?.(false), [onBusyChange]);

  const handleNotify = async (shop) => {
    setResult(null);
    if (openId === shop.subscriptionId) {
      setOpenId(null);
      return;
    }
    if (agreeing) return;
    setAgreeing(true);
    onBusyChange?.(true);
    try {
      // 서버의 최신 문자 수신 동의를 확인하고, 없으면 동의 모달에서 PUT 으로 저장한다
      const agreed = await ensureAgreement(TERMS_CODE.SMS_NOTIFICATION);
      if (agreed) {
        const shopKey = String(Number(shop.shopId));
        // 목록 조회가 실패하면 키가 없다. 카드를 열 때 한 번 더 받는다
        if (!(shopKey in useNotificationStore.getState().shopTreatments)) {
          await loadShopNames([shop.shopId]);
        }
        const treatments = useNotificationStore.getState().shopTreatments[shopKey];
        const savedId = shop.treatmentId != null ? String(shop.treatmentId) : '';
        const stillThere = selectableTreatments(treatments).some(
          (item) => String(item.treatmentId) === savedId,
        );
        setTreatmentId(stillThere ? savedId : '');
        setOpenId(shop.subscriptionId);
      }
    } finally {
      setAgreeing(false);
      onBusyChange?.(false);
    }
  };

  const handleSubmit = async (shop, { treatmentId: chosenTreatmentId, startTime, endTime }) => {
    const { ok, error } = await updateSubscription(shop.subscriptionId, {
      shopId: shop.shopId,
      treatmentId: chosenTreatmentId,
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
          const open = openId === shop.subscriptionId;
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
                    {shop.treatmentId != null && (
                      <p>{treatmentNameOf(shopTreatments[String(Number(shop.shopId))], shop.treatmentId)}</p>
                    )}
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
                    disabled={saving || agreeing}
                    onClick={() => handleNotify(shop)}
                  >
                    {open ? '접기' : '알림 받기'}
                  </Button>
                </div>
              </div>
              {open && (
                <StoreHopeForm
                  className={styles.expand}
                  treatments={
                    String(Number(shop.shopId)) in shopTreatments
                      ? shopTreatments[String(Number(shop.shopId))]
                      : null
                  }
                  treatmentId={treatmentId}
                  onTreatmentChange={(event) => setTreatmentId(event.target.value)}
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
