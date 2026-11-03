import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ACCOUNT_TYPE } from '@/constants/accountType';
import { ROUTES } from '@/constants/routes';
import { SIGNUP_HIDDEN_TERMS } from '@/constants/terms';
import { useAuthStore } from '@/store/useAuthStore';
import { useTermsStore } from '@/store/useTermsStore';
import {
  collectErrors,
  validateLoginId,
  validateMobilePhone,
  validatePassword,
  validatePasswordConfirm,
  validateRequired,
} from '@/utils/authValidation';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import AccountFields from './components/AccountFields';
import AuthCard from './components/AuthCard';
import AuthField from './components/AuthField';
import TermsAgreementFields from './components/TermsAgreementFields';
import styles from './SignupPage.module.css';

const EMPTY_FORM = { loginId: '', password: '', passwordConfirm: '', name: '', phone: '' };

/**
 * 고객 회원가입 (REQ-003). 가입하면 바로 로그인되어 홈으로 이동한다.
 * 휴대폰 번호는 관심 매장·조건 구독의 문자 알림 수신 번호로 쓰인다.
 * 필수 약관은 모두 동의해야 하고, 선택 약관(문자 수신)은 동의한 경우에만 보낸다.
 * 초보 시술 촬영 동의는 가입 화면에 보이지 않고 첫 초보 시술 예약 때 받는다.
 */
export default function SignupPage() {
  const navigate = useNavigate();
  const status = useAuthStore((state) => state.status);
  const error = useAuthStore((state) => state.error);
  const signup = useAuthStore((state) => state.signup);
  const clearError = useAuthStore((state) => state.clearError);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const allTerms = useTermsStore((state) => state.terms);
  const termsStatus = useTermsStore((state) => state.status);
  const termsError = useTermsStore((state) => state.error);
  const loadTerms = useTermsStore((state) => state.loadTerms);
  const [agreedTerms, setAgreedTerms] = useState([]);
  const terms = useMemo(
    () => allTerms.filter((item) => !SIGNUP_HIDDEN_TERMS.includes(item.code)),
    [allTerms],
  );

  useEffect(() => clearError, [clearError]);

  useEffect(() => {
    loadTerms();
  }, [loadTerms]);

  const handleTermsChange = (codes) => {
    setAgreedTerms(codes);
    setFieldErrors((prev) => ({ ...prev, terms: '' }));
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setFieldErrors((prev) => ({ ...prev, [name]: '' }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const errors = collectErrors({
      loginId: validateLoginId(form.loginId),
      password: validatePassword(form.password),
      passwordConfirm: validatePasswordConfirm(form.password, form.passwordConfirm),
      name: validateRequired(form.name, '이름'),
      phone: validateMobilePhone(form.phone),
      terms: terms.some((item) => item.required && !agreedTerms.includes(item.code))
        ? '필수 약관에 모두 동의해 주세요.'
        : '',
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    try {
      await signup(ACCOUNT_TYPE.CUSTOMER, {
        loginId: form.loginId,
        password: form.password,
        name: form.name.trim(),
        phone: form.phone,
        agreedTerms,
      });
      navigate(ROUTES.HOME, { replace: true });
    } catch {
      // 오류 문구는 store 의 error 로 화면에 표시한다
    }
  };

  const isSubmitting = status === 'loading';
  const isTermsReady = termsStatus === 'succeeded';

  return (
    <AuthCard
      title="회원가입"
      description="가입하면 바로 빈자리 예약과 알림 구독을 이용할 수 있어요."
    >
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <AccountFields value={form} onChange={handleChange} errors={fieldErrors} />
        <AuthField
          label="이름"
          name="name"
          value={form.name}
          onChange={handleChange}
          autoComplete="name"
          maxLength={50}
          error={fieldErrors.name}
        />
        <AuthField
          label="휴대폰 번호"
          name="phone"
          type="tel"
          value={form.phone}
          onChange={handleChange}
          autoComplete="tel"
          placeholder="010-1234-5678"
          hint="빈자리·예약 확정·취소 문자를 받을 번호예요. 번호가 없으면 문자는 가지 않습니다."
          error={fieldErrors.phone}
        />
        {isTermsReady && (
          <TermsAgreementFields
            terms={terms}
            agreed={agreedTerms}
            onChange={handleTermsChange}
            error={fieldErrors.terms}
          />
        )}
        {termsStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {termsError.message}{' '}
            <Button size="small" type="button" onClick={loadTerms}>
              다시 불러오기
            </Button>
          </Alert>
        )}
        {error && (
          <Alert as="p" tone="error" role="alert">
            {error.message}
          </Alert>
        )}
        <Button variant="primary" type="submit" wide disabled={isSubmitting || !isTermsReady}>
          {isSubmitting ? '가입하는 중…' : '가입하기'}
        </Button>
      </form>

      <p className={styles.links}>
        이미 회원이신가요? <Link to={ROUTES.LOGIN}>로그인</Link>
        <span aria-hidden="true"> · </span>
        매장을 운영하시나요? <Link to={ROUTES.SHOP_SIGNUP}>매장 가입</Link>
      </p>
    </AuthCard>
  );
}
