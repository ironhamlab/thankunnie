import { useState } from 'react';
import Button from '@/components/common/Button';
import Alert from '@/components/common/Alert';
import styles from './OwnerImageEditor.module.css';

export default function OwnerImageEditor({ imageUrl, isSaving, onSave }) {
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const save = async (file) => {
    setError('');
    setMessage('');
    if (file && (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
      setError('JPEG 또는 PNG 이미지를 5MB 이하로 선택해 주세요.');
      return;
    }
    try {
      await onSave(file);
      setMessage(file ? '대표 이미지를 저장했습니다.' : '대표 이미지를 삭제했습니다.');
    } catch (failure) {
      setError(failure.message ?? '이미지 요청에 실패했습니다.');
    }
  };
  const src = imageUrl
    ? new URL(
        imageUrl,
        new URL(
          import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
          window.location.origin,
        ),
      ).href
    : null;
  return (
    <div className="b-stack">
      {src ? (
        <img className={styles.preview} src={src} alt="등록된 대표 이미지" />
      ) : (
        <p>등록된 대표 이미지가 없습니다.</p>
      )}
      <label>
        대표 이미지 업로드·교체 (JPEG·PNG, 최대 5MB)
        <input
          type="file"
          accept="image/jpeg,image/png"
          disabled={isSaving}
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = '';
            if (file) save(file);
          }}
        />
      </label>
      <Button type="button" disabled={isSaving || !imageUrl} onClick={() => save(null)}>
        이미지 삭제
      </Button>
      {error && (
        <Alert tone="error" role="alert">
          {error}
        </Alert>
      )}
      {message && (
        <Alert tone="success" role="status">
          {message}
        </Alert>
      )}
    </div>
  );
}
