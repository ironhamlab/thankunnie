const TONE_CLASS = {
  success: 'b-success',
  error: 'b-error',
};

/**
 * 처리 결과 메시지 (성공·오류).
 * 스타일은 아직 공통 클래스(b-success, b-error, assets/styles/design.css)를 사용한다.
 *
 * @param {'success'|'error'} tone 메시지 종류
 */
export default function Alert({ as: Component = 'div', tone, className = '', ...props }) {
  const classes = [TONE_CLASS[tone], className].filter(Boolean).join(' ');

  return <Component className={classes} {...props} />;
}
