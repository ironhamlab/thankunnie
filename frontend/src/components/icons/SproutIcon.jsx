/**
 * 새싹 아이콘. 24×24 격자 기준이며 색은 글자색(currentColor)을 따른다.
 *
 * @param {number} [size] 가로·세로 크기(px). 16·20·24 중에서 쓴다
 */
export default function SproutIcon({ size = 24, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d="M12 21V10" />
      <path d="M12 14C12 9.500 8.500 7 3.500 7C3.500 11.500 6.500 14 12 14Z" />
      <path d="M12 10C12 6 15 3.500 20.500 3.500C20.500 8 17.500 10.500 12 10Z" />
    </svg>
  );
}
