import Button from '@/components/common/Button';
import AuthField from './AuthField';
import styles from './VerificationMessage.module.css';

export default function SignupVerificationFields({ verification, mode }) {
  if (mode === 'loginId') {
    return (
      <div className="b-span-two">
        <Button
          type="button"
          size="small"
          disabled={verification.idBusy}
          onClick={verification.handleCheckLoginId}
        >
          {verification.idBusy ? '확인 중…' : '아이디 중복 확인'}
        </Button>
        <p role="status" className={styles.message}>
          {verification.idMessage}
        </p>
      </div>
    );
  }
  return (
    <div className="b-span-two">
      {/* 인증을 마치면 재발송 안내가 필요 없으므로 버튼을 숨긴다. 번호를 바꾸면 인증이 풀려 다시 보인다 */}
      {!verification.isPhoneVerified && (
        <Button
          type="button"
          size="small"
          disabled={verification.phoneBusy || verification.resendRemaining > 0}
          onClick={verification.handleSendCode}
        >
          {verification.resendRemaining > 0
            ? `${verification.resendRemaining}초 후 재발송`
            : verification.challenge
              ? '인증번호 재발송'
              : '인증번호 발송'}
        </Button>
      )}
      {verification.challenge && !verification.isPhoneVerified && (
        <>
          <AuthField
            label="인증번호"
            name="verificationCode"
            value={verification.code}
            onChange={(event) =>
              verification.setCode(event.target.value.replace(/\D/g, '').slice(0, 6))
            }
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            hint={`남은 시간 ${verification.remaining}초`}
          />
          <Button
            type="button"
            size="small"
            disabled={verification.phoneBusy || verification.remaining === 0}
            onClick={verification.handleVerifyCode}
          >
            인증번호 확인
          </Button>
        </>
      )}
      <p role="status" className={styles.message}>
        {verification.challenge && verification.remaining === 0
          ? '인증이 만료되었습니다. 새 인증번호를 요청해 주세요.'
          : verification.phoneMessage}
      </p>
    </div>
  );
}
