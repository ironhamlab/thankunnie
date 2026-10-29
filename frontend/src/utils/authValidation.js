/**
 * 가입 입력값 사전 검사. 백엔드 가입 DTO(AuthInputRules)와 같은 규칙이다.
 * 서버도 같은 규칙으로 다시 검사하며, 여기서는 400 응답 전에 필드별로 알려주는 용도다.
 * 각 함수는 문제가 없으면 빈 문자열을, 있으면 안내 문구를 돌려준다.
 */
const LOGIN_ID_PATTERN = /^[a-z0-9]{4,20}$/;
const MOBILE_PHONE_PATTERN = /^01[016789]-?\d{3,4}-?\d{4}$/;
// BCrypt 가 처리할 수 있는 비밀번호 최대 길이 (UTF-8). 한글은 글자당 3바이트
const PASSWORD_MAX_BYTES = 72;

export const validateLoginId = (value) =>
  LOGIN_ID_PATTERN.test(value) ? '' : '아이디는 영문 소문자·숫자 4~20자로 입력해 주세요.';

export const validatePassword = (value) => {
  if (value.length < 8 || value.length > 64) {
    return '비밀번호는 8~64자로 입력해 주세요.';
  }
  if (new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES) {
    return '비밀번호가 너무 깁니다. 한글은 24자, 영문·숫자는 64자까지 입력할 수 있습니다.';
  }
  return '';
};

export const validatePasswordConfirm = (password, confirm) =>
  password === confirm ? '' : '비밀번호가 일치하지 않습니다.';

export const validateMobilePhone = (value) =>
  MOBILE_PHONE_PATTERN.test(value) ? '' : '휴대폰 번호를 확인해 주세요. 예: 010-1234-5678';

export const validateRequired = (value, label) =>
  value.trim() ? '' : `${label}을(를) 입력해 주세요.`;

/** 직접 입력한 좌표가 범위 안의 숫자인지. 비어 있으면 위치 확인 오류로 따로 알리므로 통과시킨다. */
export const validateCoordinate = (value, { label, min, max }) => {
  if (value === '') {
    return '';
  }
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max
    ? ''
    : `${label}는 ${min}~${max} 사이 숫자로 입력해 주세요.`;
};

/** { 필드: 오류 문구 } 중 문구가 있는 것만 남긴다. 결과가 빈 객체면 통과다. */
export const collectErrors = (checks) =>
  Object.fromEntries(Object.entries(checks).filter(([, message]) => message));
