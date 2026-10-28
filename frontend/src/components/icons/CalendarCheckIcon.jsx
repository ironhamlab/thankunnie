/**
 * 체크 달력 아이콘(빈자리·일정). 24×24 격자 기준이며 색은 글자색(currentColor)을 따른다.
 *
 * @param {number} [size] 가로·세로 크기(px). 16·20·24 중에서 쓴다
 */
export default function CalendarCheckIcon({ size = 24, ...props }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      {...props}
    >
      <path d="M10.95 18.35 L7.4 14.8 L8.85 13.35 L10.95 15.45 L15.15 11.25 L16.6 12.7 L10.95 18.35 V18.35 M5 22 C4.45 22 3.979 21.804 3.588 21.412 C3.196 21.021 3 20.55 3 20 V6 C3 5.45 3.196 4.979 3.588 4.587 C3.979 4.196 4.45 4 5 4 H6 V2 H8 V4 H16 V2 H18 V4 H19 C19.55 4 20.021 4.196 20.412 4.587 C20.804 4.979 21 5.45 21 6 V20 C21 20.55 20.804 21.021 20.412 21.412 C20.021 21.804 19.55 22 19 22 H5 V22 M5 20 H19 V20 V20 V10 H5 V20 V20 V20 V20 M5 8 H19 V6 V6 V6 H5 V6 V6 V8 V8 M5 8 V6 V6 V6 V6 V6 V6 V8 V8 V8" />
    </svg>
  );
}
