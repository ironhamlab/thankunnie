/**
 * 막대 차트 아이콘(운영 현황). 24×24 격자 기준이며 색은 글자색(currentColor)을 따른다.
 *
 * @param {number} [size] 가로·세로 크기(px). 16·20·24 중에서 쓴다
 */
export default function ChartIcon({ size = 24, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      {...props}
    >
      <path d="M3 21 V19 H21 V21 H3 M5 17 V10 H8 V17 H5 M10.5 17 V4 H13.5 V17 H10.5 M16 17 V7 H19 V17 H16" />
    </svg>
  );
}
