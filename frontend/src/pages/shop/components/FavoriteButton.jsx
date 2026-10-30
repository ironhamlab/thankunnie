import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { TERMS_CODE } from '@/constants/terms';
import { useAuthStore } from '@/store/useAuthStore';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useTermsStore } from '@/store/useTermsStore';
import { getDevTestUser } from '@/utils/devTestUser';
import {
  buildStoreRequest,
  isShopSubscribed,
  storeSubscriptionsOf,
} from '@/utils/notificationRules';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import StoreHopeForm from '@/components/notification/StoreHopeForm';
import styles from '../ShopDetailPage.module.css';

/**
 * 매장 찜. 아직 찜하지 않았으면 빈자리 알림 여부를 묻는다.
 * 아니요는 희망 시간 없는 업장 구독으로 저장한다. 새로고침해도 남고, 알림은 가지 않는다.
 * 예는 문자 수신 동의를 확인(없으면 동의 모달)한 뒤 이 팝업에서 희망 시간을 넣어 업장 구독을 추가한다.
 * 문자 수신에 동의하지 않으면 아니요와 같이 희망 시간 없는 구독(찜)만 저장한다.
 * 이미 찜이거나 알림 중이면 최근 업장 구독 하나만 해제한다. 남은 구독이 있으면 하트는 유지된다.
 * 로그인하지 않았으면 로그인 화면으로 보낸다.
 */
export default function FavoriteButton({ shopId, shopName }) {
  const navigate = useNavigate();
  const location = useLocation();
  const token = useAuthStore((state) => state.token);
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const addSubscription = useNotificationStore((state) => state.addSubscription);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const ensureAgreement = useTermsStore((state) => state.ensureAgreement);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [formError, setFormError] = useState(null);
  const [step, setStep] = useState(null);
  // PrivateRoute 와 같이 개발 서버 테스트 계정도 로그인으로 본다
  const loggedIn = Boolean(token || getDevTestUser());

  useEffect(() => {
    if (loggedIn) loadSubscriptions();
  }, [loggedIn, loadSubscriptions]);

  // 최근 등록이 앞이다. 하트는 남은 업장 구독이 하나라도 있으면 켜 둔다. 해제는 맨 앞 하나만 지운다
  const storeSubscriptions = loggedIn ? storeSubscriptionsOf(subscriptions, shopId) : [];
  const favorite = loggedIn && isShopSubscribed(subscriptions, shopId);

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
      setError(null);
      setNotice(null);
      setFormError(null);
      setStep('ask');
      return;
    }
    setError(null);
    setNotice(null);
    const target = storeSubscriptions[0];
    if (!target) return;
    const result = await removeSubscription(target.subscriptionId);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    if (storeSubscriptions.length > 1) {
      setNotice('이 매장의 다른 알림 구독은 그대로 두었습니다.');
    }
  };

  const handleDecline = async () => {
    const result = await addSubscription(buildStoreRequest(shopId, null));
    if (!result.ok) {
      setError(result.error.message);
    }
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
    const result = await addSubscription(buildStoreRequest(shopId, { startTime, endTime }));
    if (!result.ok) {
      setFormError(result.error.message);
      return;
    }
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
        {notice && (
          <p role="status" className={styles.favoriteError}>
            {notice}
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
                : '찜한 매장에 예약 가능한 자리가 나면 문자로 알려드릴까요?'
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
              <p className={styles.doneCopy}>
                희망 시간에 빈자리가 나면 가입한 휴대폰 번호로 문자가 갑니다.
              </p>
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
