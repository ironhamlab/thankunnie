import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useLogout } from '@/hooks/useLogout';
import { useUserStore } from '@/store/useUserStore';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './WithdrawDialog.module.css';

const HAS_RESERVATION = 'USER_002';

const NOTICES = [
  '결제 대기·방문 전 예약이나 환불 처리 중인 취소가 있으면 탈퇴할 수 없어요.',
  '받은 알림, 빈자리 알림 조건, 찜이 모두 사라져요.',
  '지난 예약 기록은 매장 정산을 위해 "탈퇴회원"으로 남아요.',
  '탈퇴한 아이디는 다시 쓸 수 없어요. 같은 휴대폰 번호로는 다시 가입할 수 있어요.',
];

/**
 * 회원 탈퇴 확인 창. 안내 확인 → 비밀번호 입력 → 탈퇴 완료 안내 순서로 보여준다.
 * 완료 안내를 닫으면 로그아웃하고 홈으로 이동한다.
 */
export default function WithdrawDialog({ onClose }) {
  const withdraw = useUserStore((s) => s.withdraw);
  const withdrawStatus = useUserStore((s) => s.withdrawStatus);
  const withdrawError = useUserStore((s) => s.withdrawError);
  const resetWithdraw = useUserStore((s) => s.resetWithdraw);
  const logout = useLogout();
  const [agreed, setAgreed] = useState(false);
  const [password, setPassword] = useState('');

  // 창을 다시 열었을 때 이전 오류가 남지 않게 한다
  useEffect(() => resetWithdraw, [resetWithdraw]);

  const submitting = withdrawStatus === 'loading';
  const done = withdrawStatus === 'succeeded';

  const handleSubmit = async (event) => {
    event.preventDefault();
    try {
      await withdraw(password);
    } catch {
      // 오류 문구는 store 의 withdrawError 로 화면에 표시한다
    }
  };

  if (done) {
    return (
      <Dialog
        className={styles.dialog}
        title="탈퇴가 완료됐어요"
        stepKey="done"
        hideClose
        onClose={() => logout(ROUTES.HOME)}
      >
        <div className={styles.root}>
          <div className={`${styles.badge} ${styles.badgeDone}`} aria-hidden="true">
            ✓
          </div>
          <p className={styles.description}>그동안 이용해 주셔서 감사합니다.</p>
          <Button variant="primary" wide onClick={() => logout(ROUTES.HOME)}>
            홈으로
          </Button>
        </div>
      </Dialog>
    );
  }

  return (
    // 요청 중에는 닫지 않는다. 닫으면 탈퇴 결과와 화면이 어긋난다
    <Dialog
      className={styles.dialog}
      title="정말 탈퇴하시겠어요?"
      stepKey="confirm"
      plainClose
      hideClose={submitting}
      onClose={() => {
        if (!submitting) onClose();
      }}
    >
      <form className={styles.root} onSubmit={handleSubmit}>
        <div className={styles.badge} aria-hidden="true">
          !
        </div>
        <p className={styles.description}>탈퇴하기 전에 아래 내용을 확인해 주세요.</p>
        <section className={styles.content} aria-label="탈퇴 안내">
          <ul>
            {NOTICES.map((notice) => (
              <li key={notice}>{notice}</li>
            ))}
          </ul>
        </section>
        <label className={styles.agree}>
          <input
            type="checkbox"
            checked={agreed}
            onChange={(event) => setAgreed(event.target.checked)}
          />
          <span>안내 내용을 모두 확인했어요</span>
        </label>
        <label className={styles.password}>
          <span>비밀번호 확인</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="현재 비밀번호를 입력해 주세요"
            autoComplete="current-password"
            maxLength={100}
            required
          />
        </label>
        {withdrawError && (
          <div className={styles.error}>
            <Alert as="p" tone="error" role="alert">
              {withdrawError.message}
            </Alert>
            {withdrawError.code === HAS_RESERVATION && (
              <Button as={Link} size="small" to={ROUTES.MY_RESERVATIONS}>
                내 예약 보기
              </Button>
            )}
          </div>
        )}
        <div className={styles.actions}>
          <Button type="button" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button type="submit" variant="danger" disabled={!agreed || !password || submitting}>
            {submitting ? '탈퇴하는 중…' : '탈퇴하기'}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
