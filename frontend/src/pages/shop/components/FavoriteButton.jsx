import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { TERMS_CODE } from '@/constants/terms';
import { useAuthStore } from '@/store/useAuthStore';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useTermsStore } from '@/store/useTermsStore';
import { getDevTestUser } from '@/utils/devTestUser';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import StoreHopeForm from '@/components/notification/StoreHopeForm';
import styles from '../ShopDetailPage.module.css';

/**
 * 매장 찜. 아직 찜하지 않았으면 빈자리 알림 여부를 묻는다.
 * 아니요는 알림 없이 찜만 한다. 예는 문자 수신 동의를 확인(없으면 동의 모달)한 뒤 이 팝업에서 알림 조건을 추가하고,
 * 찜 목록에는 넣지 않는다. 문자 수신에 동의하지 않으면 아니요와 같이 찜만 한다.
 * 이미 찜이거나 알림 중이면 둘 다 해제한다. 로그인하지 않았으면 로그인 화면으로 보낸다.
 */
export default function FavoriteButton({ shopId, shopName }) {
  const navigate = useNavigate();
  const location = useLocation();
  const token = useAuthStore((state) => state.token);
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const favoriteShops = useNotificationStore((state) => state.favoriteShops);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const addFavoriteShop = useNotificationStore((state) => state.addFavoriteShop);
  const removeFavoriteShop = useNotificationStore((state) => state.removeFavoriteShop);
  const addSubscription = useNotificationStore((state) => state.addSubscription);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const ensureAgreement = useTermsStore((state) => state.ensureAgreement);
  const [error, setError] = useState(null);
  const [formError, setFormError] = useState(null);
  const [step, setStep] = useState(null);
  // PrivateRoute 와 같이 개발 서버 테스트 계정도 로그인으로 본다
  const loggedIn = Boolean(token || getDevTestUser());

  useEffect(() => {
    if (loggedIn) loadSubscriptions();
  }, [loggedIn, loadSubscriptions]);

  const subscription = loggedIn
    ? subscriptions.find(
        (item) => item.subscriptionType === 'STORE' && Number(item.shopId) === Number(shopId),
      )
    : undefined;
  const favorite =
    loggedIn &&
    (Boolean(subscription) ||
      favoriteShops.some((item) => Number(item.shopId) === Number(shopId)));

  const closeDialog = () => {
    setStep(null);
    setFormError(null);
  };

  const handleClick = async () => {
    if (!loggedIn) {
      navigate(ROUTES.LOGIN, { state: { from: location } });
      return;
    }
    if (!favorite) {
      setFormError(null);
      setStep('ask');
      return;
    }
    setError(null);
    removeFavoriteShop(shopId);
    if (!subscription) return;
    const result = await removeSubscription(subscription.subscriptionId);
    if (!result.ok) setError(result.error.message);
  };

  const handleDecline = () => {
    addFavoriteShop({ shopId, shopName });
    closeDialog();
  };

  // 알림 조건을 입력하기 전에 문자 수신 동의부터 받는다. 질문 창을 닫고 동의 모달만 보이게 한다
  const handleAccept = async () => {
    setStep(null);
    const agreed = await ensureAgreement(TERMS_CODE.SMS_NOTIFICATION);
    if (!agreed) {
      handleDecline();
      return;
    }
    setFormError(null);
    setStep('form');
  };

  const handleCreate = async ({ startTime, endTime }) => {
    setFormError(null);
    const result = await addSubscription({
      subscriptionType: 'STORE',
      shopId,
      categoryId: null,
      regionCode: null,
      startTime,
      endTime,
    });
    if (!result.ok) {
      setFormError(result.error.message);
      return;
    }
    // 알림을 받은 매장은 찜만 한 목록에서 뺀다
    removeFavoriteShop(shopId);
    setStep('done');
  };

  return (
    <>
      <div className={styles.favorite}>
        <button
          type="button"
          className={favorite ? 'is-selected' : undefined}
          aria-pressed={Boolean(favorite)}
          aria-label={favorite ? '찜 해제' : '찜하기'}
          disabled={saving || (loggedIn && listStatus === 'loading')}
          onClick={handleClick}
        >
          <span aria-hidden="true">{favorite ? '♥' : '♡'}</span>찜
        </button>
        {error && (
          <p role="alert" className={styles.favoriteError}>
            {error}
          </p>
        )}
      </div>
      {step && (
        <Dialog
          className={step === 'form' ? styles.alertDialogForm : styles.alertDialog}
          title={
            step === 'done'
              ? '땡 잡을 기회 완료!'
              : step === 'form'
                ? '알림 조건 추가'
                : '찜한 매장에 예약 가능한 자리가 나면 알려드릴까요?'
          }
          stepKey={step}
          hideClose={step !== 'form'}
          onClose={closeDialog}
        >
          {step === 'ask' ? (
            <>
              <p className={styles.alertShop}>{shopName}</p>
              <div className={styles.confirmActions}>
                <Button type="button" onClick={handleDecline}>
                  아니요
                </Button>
                <Button type="button" className={styles.confirmYes} onClick={handleAccept}>
                  예
                </Button>
              </div>
            </>
          ) : step === 'done' ? (
            <>
              <p className={styles.alertShop}>{shopName}</p>
              <p className={styles.doneCopy}>빈자리가 나면 이 매장을 알려드릴게요.</p>
              <div className={styles.doneActions}>
                <Button type="button" className={styles.confirmYes} onClick={closeDialog}>
                  확인
                </Button>
              </div>
            </>
          ) : (
            <>
              <p className={styles.alertShop}>{shopName}</p>
              {formError && (
                <p role="alert" className={styles.formError}>
                  {formError}
                </p>
              )}
              <StoreHopeForm onSubmit={handleCreate} saving={saving} />
            </>
          )}
        </Dialog>
      )}
    </>
  );
}
