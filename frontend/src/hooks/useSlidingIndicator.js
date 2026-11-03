import { useLayoutEffect, useRef, useState } from 'react';

/**
 * 고른 항목 뒤로 미끄러져 이동하는 선택 표시의 위치를 잰다.
 * 처음 그릴 때는 움직이지 않고, 고른 항목이 바뀐 뒤부터 움직인다.
 *
 * @param selectedKey 지금 고른 항목. 바뀌면 다시 잰다
 * @returns selectedRef 고른 항목에 붙일 ref, style 표시에 줄 스타일(잴 수 없으면 null), isMoving 움직여도 되는지
 */
export function useSlidingIndicator(selectedKey) {
  const selectedRef = useRef(null);
  const [style, setStyle] = useState(null);
  const [firstKey] = useState(selectedKey);
  const [isMoving, setIsMoving] = useState(false);

  if (selectedKey !== firstKey && !isMoving) {
    setIsMoving(true);
  }

  useLayoutEffect(() => {
    const selected = selectedRef.current;
    if (!selected) {
      setStyle(null);
      return undefined;
    }
    const measure = () =>
      setStyle({
        width: selected.offsetWidth,
        height: selected.offsetHeight,
        transform: `translate(${selected.offsetLeft}px, ${selected.offsetTop}px)`,
      });
    measure();
    // 글꼴을 늦게 받거나 화면 폭이 바뀌면 항목 크기와 줄바꿈이 달라지므로 다시 잰다
    const observer = new ResizeObserver(measure);
    observer.observe(selected);
    if (selected.parentElement) {
      observer.observe(selected.parentElement);
    }
    return () => observer.disconnect();
  }, [selectedKey]);

  return { selectedRef, style, isMoving };
}
