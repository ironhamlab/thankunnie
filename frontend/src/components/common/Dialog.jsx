import { useEffect, useId, useRef } from 'react';
import Button from '@/components/common/Button';
import styles from './Dialog.module.css';

// 점이 창 네모 안에 있는지. 창 자체의 스크롤 막대를 눌러도 target 이 창이라 위치로 바깥을 가린다
const isInside = (element, event) => {
  const rect = element.getBoundingClientRect();
  return (
    event.clientX >= rect.left &&
    event.clientX <= rect.right &&
    event.clientY >= rect.top &&
    event.clientY <= rect.bottom
  );
};

export default function Dialog({
  title,
  onClose,
  children,
  hideClose = false,
  /** 알림 관련 창만 테두리 없는 ✕ 를 오른쪽 위에 붙인다 */
  plainClose = false,
  /** 입력 중인 내용이 날아가거나 닫기가 곧 취소인 창은 false 로 두어 바깥을 눌러도 닫히지 않게 한다 */
  closeOnBackdrop = true,
  className = '',
  stepKey,
}) {
  const ref = useRef(null);
  // 창 안에서 글자를 고르다 바깥에서 손을 떼도 닫히지 않게, 누르기 시작한 곳도 바깥일 때만 닫는다
  const pressedBackdrop = useRef(false);
  // 화면 읽기 프로그램이 제목을 대화상자 이름으로 읽도록 연결한다
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const previousFocus = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);
  // 닫기 버튼을 숨긴 창(저장 중 등)은 바깥을 눌러도 닫지 않는다
  const canCloseOnBackdrop = closeOnBackdrop && !hideClose;
  // 안에 다른 창이 열려 있으면 그 창을 누른 이벤트도 여기로 올라오므로 target 이 이 창일 때만 본다
  const isBackdrop = (event) => event.target === ref.current && !isInside(ref.current, event);
  return (
    <dialog
      ref={ref}
      className={[styles.root, className].filter(Boolean).join(' ')}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onPointerDown={(e) => {
        pressedBackdrop.current = isBackdrop(e);
      }}
      onClick={(e) => {
        if (canCloseOnBackdrop && pressedBackdrop.current && isBackdrop(e)) onClose();
      }}
    >
      <div className={`beauty-app b-pad ${plainClose ? styles.body : ''}`}>
        {/* 단계 애니메이션(transform) 안에 두면 그동안 위치 기준이 바뀌어 ✕ 가 튀므로 바깥에 둔다 */}
        {plainClose && !hideClose && (
          <div className={styles.closeDock}>
            <button type="button" className={styles.close} aria-label="닫기" onClick={onClose}>
              <svg width="28" height="28" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M5 5l14 14M19 5L5 19"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.8"
                  strokeLinecap="round"
                />
              </svg>
            </button>
          </div>
        )}
        <div key={stepKey} className={stepKey == null ? undefined : styles.step}>
          <div
            className={`b-row b-panel-heading ${styles.stickyHeading} ${plainClose && !hideClose ? styles.heading : ''}`}
          >
            <h2 id={titleId}>{title}</h2>
            {!hideClose && !plainClose && (
              <Button size="small" aria-label="닫기" onClick={onClose}>
                ✕
              </Button>
            )}
          </div>
          {children}
        </div>
      </div>
    </dialog>
  );
}
