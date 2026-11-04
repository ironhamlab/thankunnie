import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Notice from '@/components/common/Notice';
import Spinner from '@/components/common/Spinner';
import styles from './QueueWaitingDialog.module.css';

/**
 * 같은 빈자리를 먼저 누른 사람이 있을 때 띄우는 대기 화면 (docs/api/reservations.md 예약 대기열).
 * 순서는 서버가 알려준 값을 그대로 보여주고, 차례가 오면 이 창이 닫히며 바로 결제 화면으로 간다.
 *
 * @param aheadCount 내 앞에 있는 사람 수. 서버가 결제 중인 사람도 한 명으로 세어 준다
 * @param paying     다른 사람이 이 시간을 잡고 결제 중인지
 * @param onCancel   대기 취소 (줄에서 나간다)
 */
export default function QueueWaitingDialog({ aheadCount, paying, onCancel }) {
  return (
    <Dialog title="예약 대기 중이에요" onClose={onCancel}>
      <Spinner />
      <p className={styles.position} aria-live="polite">
        {aheadCount > 0 ? (
          <>
            내 앞에 <strong>{aheadCount}명</strong>
          </>
        ) : (
          <strong>곧 내 차례예요</strong>
        )}
      </p>
      <Notice>
        {paying
          ? '먼저 누른 분이 결제 중이에요. 5분 안에 결제하지 않으면 순서가 넘어와요.'
          : '앞 순서가 끝나면 자동으로 결제 화면으로 넘어가요.'}
        <br />이 창을 닫거나 다른 화면으로 가면 대기가 취소돼요.
      </Notice>
      <Button wide onClick={onCancel}>
        대기 취소
      </Button>
    </Dialog>
  );
}
