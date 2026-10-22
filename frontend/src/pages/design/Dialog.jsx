import { useEffect, useRef } from 'react';

export default function Dialog({ title, onClose, children }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="b-native-dialog"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="beauty-app b-pad">
        <div className="b-row b-panel-heading">
          <h2>{title}</h2>
          <button className="b-button b-small" aria-label="닫기" onClick={onClose}>
            ✕
          </button>
        </div>
        {children}
      </div>
    </dialog>
  );
}
