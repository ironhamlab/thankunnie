const VARIANT_CLASS = {
  primary: 'b-primary',
  soft: 'b-soft',
  danger: 'b-danger',
};

/**
 * 공통 버튼. 링크 모양 버튼은 as={Link} 와 to 를 넘긴다.
 * 스타일은 아직 공통 클래스(b-button 등, assets/styles/design.css)를 사용한다.
 *
 * @param {'primary'|'soft'|'danger'} [variant] 색 종류. 생략하면 기본(흰 바탕) 버튼
 * @param {'small'} [size] 작은 버튼
 * @param {boolean} [wide] 가로 전체 너비
 */
export default function Button({
  as: Component = 'button',
  variant,
  size,
  wide = false,
  className = '',
  ...props
}) {
  const classes = [
    'b-button',
    VARIANT_CLASS[variant],
    size === 'small' && 'b-small',
    wide && 'b-wide',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return <Component className={classes} {...props} />;
}
