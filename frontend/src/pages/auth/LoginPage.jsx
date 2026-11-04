import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ACCOUNT_TYPE, ACCOUNT_TYPE_OPTIONS } from '@/constants/accountType';
import { ROUTES } from '@/constants/routes';
import { useAuthStore } from '@/store/useAuthStore';
import { getHomePathByRole, getRedirectPath, isFromOwnerArea } from '@/utils/authRedirect';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Notice from '@/components/common/Notice';
import AuthCard from './components/AuthCard';
import AuthField from './components/AuthField';
import ShopApprovalDialog from './components/ShopApprovalDialog';
import styles from './LoginPage.module.css';

const EMPTY_FORM = { loginId: '', password: '' };
const WITHDRAWN_ACCOUNT = 'AUTH_003';

// 매장 승인 대기(AUTH_011)·거절(AUTH_012)은 오류 문구 대신 팝업으로 알린다. 거절 사유는 서버 문구에 있다
const SHOP_APPROVAL_NOTICE = {
  AUTH_011: () => ({
    title: '가입 승인 대기 중',
    lines: ['승인 대기중입니다.', '관리자 승인 후 매장 센터에 로그인할 수 있어요.'],
  }),
  AUTH_012: (message) => ({ title: '매장 가입 거절', lines: [message] }),
};

/**
 * 로그인 (A-F7). 고객·관리자는 회원 계정, 매장은 매장 계정 탭으로 로그인한다.
 * 로그인 전에 보려던 화면이 있으면 그 화면으로, 없으면 역할별 첫 화면으로 이동한다.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from;
  const token = useAuthStore((state) => state.token);
  const user = useAuthStore((state) => state.data);
  const status = useAuthStore((state) => state.status);
  const error = useAuthStore((state) => state.error);
  const login = useAuthStore((state) => state.login);
  const clearSession = useAuthStore((state) => state.clearSession);
  const clearError = useAuthStore((state) => state.clearError);
  const [accountType, setAccountType] = useState(
    isFromOwnerArea(from) ? ACCOUNT_TYPE.SHOP : ACCOUNT_TYPE.CUSTOMER,
  );
  const [form, setForm] = useState(EMPTY_FORM);

  // 다른 화면으로 갔다가 돌아왔을 때 이전 로그인 오류가 남지 않게 한다
  useEffect(() => clearError, [clearError]);

  const isSubmitting = status === 'loading';
  const isShop = accountType === ACCOUNT_TYPE.SHOP;
  // 탈퇴 계정은 비밀번호가 맞을 때만 이 코드가 오므로, 다시 가입할 수 있다는 안내를 창으로 띄운다
  const isWithdrawn = error?.code === WITHDRAWN_ACCOUNT;

  const handleTypeChange = (type) => {
    setAccountType(type);
    setForm(EMPTY_FORM);
    clearError();
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    try {
      const loggedIn = await login(accountType, {
        loginId: form.loginId.trim(),
        password: form.password,
      });
      navigate(getRedirectPath(from, loggedIn.role), { replace: true });
    } catch {
      // 오류 문구는 store 의 error 로 화면에 표시한다
    }
  };

  const approvalNotice = error && SHOP_APPROVAL_NOTICE[error.code]?.(error.message);

  if (token) {
    return (
      <AuthCard title="로그인">
        <Notice>
          <p>이미 로그인되어 있어요.</p>
        </Notice>
        <div className={styles.actions}>
          <Button type="button" onClick={clearSession}>
            로그아웃
          </Button>
          <Button as={Link} variant="primary" to={getHomePathByRole(user?.role)}>
            {user?.role === 'OWNER' ? '매장 센터로' : '홈으로'}
          </Button>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="로그인" description="땡잡은 언니에서 빈자리 예약을 이어가세요.">
      <div className={styles.tabs} role="tablist" aria-label="계정 종류">
        {ACCOUNT_TYPE_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={accountType === option.value}
            onClick={() => handleTypeChange(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      <form className={styles.form} onSubmit={handleSubmit}>
        <AuthField
          label={isShop ? '매장 아이디' : '아이디'}
          name="loginId"
          value={form.loginId}
          onChange={handleChange}
          autoComplete="username"
          required
        />
        <AuthField
          label="비밀번호"
          name="password"
          type="password"
          value={form.password}
          onChange={handleChange}
          autoComplete="current-password"
          required
        />
        {error && !isWithdrawn && !approvalNotice && (
          <Alert as="p" tone="error" role="alert">
            {error.message}
          </Alert>
        )}
        <Button variant="primary" type="submit" wide disabled={isSubmitting}>
          {isSubmitting ? '로그인하는 중…' : '로그인'}
        </Button>
      </form>

      <p className={styles.signup}>
        {isShop ? '매장을 처음 등록하시나요?' : '아직 회원이 아니신가요?'}{' '}
        <Link to={isShop ? ROUTES.SHOP_SIGNUP : ROUTES.SIGNUP}>
          {isShop ? '매장 가입' : '회원가입'}
        </Link>
      </p>

      {import.meta.env.DEV && (
        <Notice>
          <small>
            개발용 테스트 계정 · 비밀번호 test1234! · 고객 customer1, customer2, 관리자 admin ·
            매장 owner1, owner2
          </small>
        </Notice>
      )}

      {isWithdrawn && (
        <Dialog title="탈퇴한 계정이에요" onClose={clearError}>
          <p>{error.message}</p>
          <div className={styles.actions}>
            <Button type="button" onClick={clearError}>
              닫기
            </Button>
            <Button as={Link} variant="primary" to={ROUTES.SIGNUP}>
              회원가입
            </Button>
          </div>
        </Dialog>
      )}

      {approvalNotice && (
        <ShopApprovalDialog
          title={approvalNotice.title}
          lines={approvalNotice.lines}
          onClose={clearError}
        />
      )}
    </AuthCard>
  );
}
