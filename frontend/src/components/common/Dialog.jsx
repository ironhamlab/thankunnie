import { useEffect, useRef } from 'react';
import Button from '@/components/common/Button';
import styles from './Dialog.module.css';

export default function Dialog({ title, onClose, children }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    const previousFocus = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={styles.root}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="beauty-app b-pad">
        <div className="b-row b-panel-heading">
          <h2>{title}</h2>
          <Button size="small" aria-label="닫기" onClick={onClose}>
            ✕
          </Button>
        </div>
        {children}
      </div>
    </dialog>
  );
}
