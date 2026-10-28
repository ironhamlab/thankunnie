/**
 * 안내 문구 상자 (연한 배경의 정보 안내).
 * 스타일은 아직 공통 클래스(b-notice, assets/styles/design.css)를 사용한다.
 */
export default function Notice({ as: Component = 'div', className = '', ...props }) {
  const classes = ['b-notice', className].filter(Boolean).join(' ');

  return <Component className={classes} {...props} />;
}
