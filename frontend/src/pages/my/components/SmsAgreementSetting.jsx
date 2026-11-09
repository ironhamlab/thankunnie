import { useEffect, useState } from 'react';
import { TERMS_CODE } from '@/constants/terms';
import { useTermsStore } from '@/store/useTermsStore';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './SmsAgreementSetting.module.css';

/**
 * 마이페이지 알림 문자 수신 설정. 체크를 끄면 확인 후 동의를 철회하고,
 * 다시 켜면 알림 등록 때와 같은 동의 모달(TermsAgreementDialog)로 동의를 받는다.
 */
export default function SmsAgreementSetting() {
  const smsAgreement = useTermsStore((state) => state.smsAgreement);
  const termsPending = useTermsStore((state) => state.pending);
  const loadSmsAgreement = useTermsStore((state) => state.loadSmsAgreement);
  const withdrawSmsAgreement = useTermsStore((state) => state.withdrawSmsAgreement);
  const resetSmsAgreement = useTermsStore((state) => state.resetSmsAgreement);
  const requestAgreement = useTermsStore((state) => state.requestAgreement);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    loadSmsAgreement();
    return resetSmsAgreement;
  }, [loadSmsAgreement, resetSmsAgreement]);

  const { status, agreed, saving, error } = smsAgreement;
  const loaded = status === 'succeeded';

  // 결과는 체크 상태로 보이므로 따로 성공 문구를 띄우지 않는다
  const handleChange = (event) => {
    if (!event.target.checked) {
      setConfirming(true);
      return;
    }
    // 동의 모달이 저장까지 끝내고 스토어의 agreed 를 켠다
    requestAgreement(TERMS_CODE.SMS_NOTIFICATION);
  };

  const handleWithdraw = async () => {
    // 실패하면 창을 열어 두고 안에 오류를 보여 준다
    const { ok } = await withdrawSmsAgreement();
    if (ok) setConfirming(false);
  };

  return (
    <section className={styles.root} aria-label="알림 문자 수신 설정">
      <label className={styles.toggle}>
        <input
          type="checkbox"
          checked={loaded && agreed}
          disabled={!loaded || saving || Boolean(termsPending)}
          onChange={handleChange}
        />
        <span>
          <strong>알림 문자 수신</strong>
          <small>찜한 매장·관심 조건에 빈자리가 생기면 문자로 알려 드려요.</small>
        </span>
      </label>
      {status === 'loading' && <p role="status">문자 수신 설정을 불러오는 중입니다.</p>}
      {error && !confirming && (
        <Alert as="p" tone="error" role="alert">
          {error.message}
        </Alert>
      )}
      {confirming && (
        <Dialog
          title="알림 문자 수신을 철회할까요?"
          hideClose={saving}
          onClose={() => {
            if (!saving) setConfirming(false);
          }}
        >
          <p className={styles.description}>
            철회하면 빈자리 알림 문자를 더 이상 보내지 않아요. 등록한 알림 조건과 찜은 그대로 남고,
            언제든 다시 체크해 수신할 수 있어요. 예약 확정·취소 안내 문자는 계속 받아요.
          </p>
          {error && (
            <Alert as="p" tone="error" role="alert">
              {error.message}
            </Alert>
          )}
          <div className={styles.actions}>
            <Button onClick={() => setConfirming(false)} disabled={saving}>
              계속 받을게요
            </Button>
            <Button variant="primary" onClick={handleWithdraw} disabled={saving}>
              {saving ? '철회하는 중…' : '수신 철회'}
            </Button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
