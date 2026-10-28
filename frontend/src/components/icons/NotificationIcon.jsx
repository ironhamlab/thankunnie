/**
 * 알림 아이콘(종 모양). 색은 글자색(currentColor)을 따르고, 크기는 사용하는 쪽 CSS 에서 정한다.
 */
export default function NotificationIcon(props) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      aria-hidden="true"
      {...props}
    >
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4" />
    </svg>
  );
}
