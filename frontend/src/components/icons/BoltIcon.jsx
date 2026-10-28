/**
 * 번개 아이콘(즉시 등록). 24×24 격자 기준이며 색은 글자색(currentColor)을 따른다.
 *
 * @param {number} [size] 가로·세로 크기(px). 16·20·24 중에서 쓴다
 */
export default function BoltIcon({ size = 24, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      {...props}
    >
      <path d="M10.55 18.2 L15.725 12 H11.725 L12.45 6.325 L7.825 13 H11.3 L10.55 18.2 V18.2 M8 22 L9 15 H4 L13 2 H15 L14 10 H20 L10 22 H8 V22 M11.775 12.25 V12.25 V12.25 V12.25 V12.25 V12.25 V12.25 V12.25" />
    </svg>
  );
}
