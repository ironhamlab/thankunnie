import { useRef } from 'react';
import Button from '@/components/common/Button';
import styles from './OwnerImageEditor.module.css';

export default function ImageFileInput({ disabled, hasImage, fileName, onSelect }) {
  const inputRef = useRef(null);
  return (
    <div className={styles.uploadBox}>
      <div className={styles.uploadCopy}>
        <strong>대표 이미지</strong>
        <span className={styles.uploadHint}>JPEG 또는 PNG · 최대 5MB</span>
        {fileName && (
          <span className={styles.fileName} title={fileName}>
            {fileName}
          </span>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        hidden
        disabled={disabled}
        aria-label="대표 이미지 파일 선택"
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) onSelect(file);
        }}
      />
      <Button
        type="button"
        variant="soft"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
      >
        {hasImage ? '사진 변경' : '사진 선택'}
      </Button>
    </div>
  );
}
