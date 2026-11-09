/**
 * 목록이 비었거나 불러오는 중·찾을 수 없음 등을 알리는 가운데 정렬 영역.
 * 스타일은 아직 공통 클래스(b-empty, assets/styles/design.css)를 사용한다.
 */
export default function EmptyState({ as: Component = 'div', className = '', ...props }) {
  const classes = ['b-empty', className].filter(Boolean).join(' ');

  return <Component className={classes} {...props} />;
}
