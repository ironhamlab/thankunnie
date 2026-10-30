import { useTermsStore } from '@/store/useTermsStore';
import { AGREEMENT_DIALOG, TERMS_CONTENT } from '@/constants/terms';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './TermsAgreementDialog.module.css';

/**
 * 선택 약관(문자 수신·모델 촬영) 동의 모달. App 에 하나만 두고 useTermsStore.requestAgreement 로 연다.
 * 구독 등록·모델 시술 예약이 동의 없이 막혔을 때(withTermsAgreement) 뜬다.
 */
export default function TermsAgreementDialog() {
  const pending = useTermsStore((state) => state.pending);
  const confirmAgreement = useTermsStore((state) => state.confirmAgreement);
  const cancelAgreement = useTermsStore((state) => state.cancelAgreement);

  if (!pending) return null;

  const dialog = AGREEMENT_DIALOG[pending.termsCode];
  const content = TERMS_CONTENT[pending.termsCode];

  return (
    <Dialog title={dialog?.title ?? '약관 동의'} onClose={cancelAgreement}>
      <div className={styles.root}>
        {dialog?.description && <p className={styles.description}>{dialog.description}</p>}
        {content && (
          <section className={styles.content} aria-label={content.summary}>
            <h3>{content.summary}</h3>
            <p>{content.body}</p>
          </section>
        )}
        {pending.error && (
          <Alert as="p" tone="error" role="alert">
            {pending.error.message}
          </Alert>
        )}
        <div className={styles.actions}>
          <Button onClick={cancelAgreement} disabled={pending.saving}>
            다음에 할게요
          </Button>
          <Button variant="primary" onClick={confirmAgreement} disabled={pending.saving}>
            {pending.saving ? '저장하는 중…' : '동의하고 계속'}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
