/**
 * 마이페이지 아이콘(사람 모양). 색은 글자색(currentColor)을 따르고, 크기는 사용하는 쪽 CSS 에서 정한다.
 */
export default function ProfileIcon(props) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      aria-hidden="true"
      {...props}
    >
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7" />
    </svg>
  );
}
