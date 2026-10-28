const TONE_CLASS = {
  neutral: 'b-neutral',
  success: 'b-green',
};

/**
 * 상태·안내용 작은 라벨.
 * 스타일은 아직 공통 클래스(b-badge 등, assets/styles/design.css)를 사용한다.
 *
 * @param {'neutral'|'success'} [tone] 종류. 생략하면 기본(강조) 라벨
 * @param {string} [className] 상태별 색처럼 값에 따라 바뀌는 클래스
 */
export default function Badge({ tone, className = '', ...props }) {
  const classes = ['b-badge', TONE_CLASS[tone], className].filter(Boolean).join(' ');

  return <span className={classes} {...props} />;
}
