import AuthField from './AuthField';

/**
 * 가입 공통 계정 입력 (아이디·비밀번호·비밀번호 확인). 고객·매장 가입이 같은 규칙을 쓴다.
 *
 * @param {object} value { loginId, password, passwordConfirm }
 * @param {string} [loginIdLabel] 아이디 칸 이름
 * @param {string} [loginIdClassName] 아이디 칸 배치용 클래스 (매장 가입의 b-span-two 등)
 */
export default function AccountFields({
  value,
  onChange,
  errors,
  loginIdLabel = '아이디',
  loginIdClassName,
}) {
  return (
    <>
      <AuthField
        className={loginIdClassName}
        label={loginIdLabel}
        name="loginId"
        value={value.loginId}
        onChange={onChange}
        autoComplete="username"
        hint="영문 소문자·숫자 4~20자"
        error={errors.loginId}
      />
      <AuthField
        label="비밀번호"
        name="password"
        type="password"
        value={value.password}
        onChange={onChange}
        autoComplete="new-password"
        hint="8~64자"
        error={errors.password}
      />
      <AuthField
        label="비밀번호 확인"
        name="passwordConfirm"
        type="password"
        value={value.passwordConfirm}
        onChange={onChange}
        autoComplete="new-password"
        error={errors.passwordConfirm}
      />
    </>
  );
}
