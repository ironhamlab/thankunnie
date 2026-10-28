/**
 * 흰 바탕 상자(패널). 기본 태그는 div 이며 section·article 등은 as 로 바꾼다.
 * 스타일은 아직 공통 클래스(b-panel, b-pad, assets/styles/design.css)를 사용한다.
 *
 * @param {boolean} [padded] 안쪽 여백 적용
 */
export default function Panel({ as: Component = 'div', padded = false, className = '', ...props }) {
  const classes = ['b-panel', padded && 'b-pad', className].filter(Boolean).join(' ');

  return <Component className={classes} {...props} />;
}
