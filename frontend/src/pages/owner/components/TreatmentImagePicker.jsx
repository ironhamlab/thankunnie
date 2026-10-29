import { useEffect, useState } from 'react';
import Button from '@/components/common/Button';
import Alert from '@/components/common/Alert';
import ImageFileInput from './ImageFileInput';
import RepresentativeImage from './RepresentativeImage';
import styles from './OwnerImageEditor.module.css';

export default function TreatmentImagePicker({ file, disabled, onChange }) {
  const [preview, setPreview] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    if (!file) {
      setPreview('');
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  return (
    <div className="b-stack b-span-two">
      <ImageFileInput
        disabled={disabled}
        hasImage={Boolean(file)}
        fileName={file?.name}
        onSelect={(selected) => {
          if (
            !['image/jpeg', 'image/png'].includes(selected.type) ||
            selected.size > 5 * 1024 * 1024
          ) {
            setError('JPEG 또는 PNG 이미지를 5MB 이하로 선택해 주세요.');
            return;
          }
          setError('');
          onChange(selected);
        }}
      />
      {preview && (
        <img src={preview} className={styles.preview} alt="등록할 시술 이미지 미리보기" />
      )}
      {!preview && <RepresentativeImage className={styles.preview} name="시술" />}
      {file && (
        <Button type="button" disabled={disabled} onClick={() => onChange(null)}>
          이미지 선택 취소
        </Button>
      )}
      {error && (
        <Alert tone="error" role="alert">
          {error}
        </Alert>
      )}
      <small>저장을 누르면 시술 정보와 함께 등록됩니다.</small>
    </div>
  );
}
