/**
 * 하트 아이콘. 24×24 격자 기준이며 색은 글자색(currentColor)을 따른다.
 *
 * @param {number} [size] 가로·세로 크기(px). 16·20·24 중에서 쓴다
 */
export default function HeartIcon({ size = 24, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      aria-hidden="true"
      {...props}
    >
      <path d="M12 20s-7-4.4-7-9a4 4 0 0 1 7-2 4 4 0 0 1 7 2c0 4.6-7 9-7 9Z" />
    </svg>
  );
}
