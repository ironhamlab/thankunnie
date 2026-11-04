import { useState } from 'react';
import Button from '@/components/common/Button';
import Alert from '@/components/common/Alert';
import ImageFileInput from './ImageFileInput';
import RepresentativeImage from '@/components/common/RepresentativeImage';
import styles from './OwnerImageEditor.module.css';

export default function OwnerImageEditor({ imageUrl, isSaving, onSave, kind = 'treatment' }) {
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
  return (
    <div className="b-stack">
      <div className={styles.editor}>
        <figure className={styles.currentImage}>
          <RepresentativeImage
            key={imageUrl ?? 'default'}
            imageUrl={imageUrl}
            kind={kind}
            name={kind === 'shop' ? '매장' : '시술'}
            className={styles.preview}
          />
          <figcaption>{imageUrl ? '현재 등록된 이미지' : '기본 이미지'}</figcaption>
        </figure>
        <div className={styles.editorControls}>
          <ImageFileInput disabled={isSaving} hasImage={Boolean(imageUrl)} onSelect={save} />
          <Button type="button" disabled={isSaving || !imageUrl} onClick={() => save(null)}>
            이미지 삭제
          </Button>
        </div>
      </div>
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
