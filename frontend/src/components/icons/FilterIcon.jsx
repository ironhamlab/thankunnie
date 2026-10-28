/**
 * 조절 막대 아이콘(필터·설정). 24×24 격자 기준이며 색은 글자색(currentColor)을 따른다.
 *
 * @param {number} [size] 가로·세로 크기(px). 16·20·24 중에서 쓴다
 */
export default function FilterIcon({ size = 24, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      {...props}
    >
      <path d="M10.889 22 V15.333 H13.111 V17.556 H22 V19.778 H13.111 V22 H10.889 V22 M2 19.778 V17.556 H8.667 V19.778 H2 V19.778 M6.444 15.333 V13.111 H2 V10.889 H6.444 V8.667 H8.667 V15.333 H6.444 V15.333 M10.889 13.111 V10.889 H22 V13.111 H10.889 V13.111 M15.333 8.667 V2 H17.556 V4.222 H22 V6.444 H17.556 V8.667 H15.333 V8.667 M2 6.444 V4.222 H13.111 V6.444 H2 V6.444" />
    </svg>
  );
}
