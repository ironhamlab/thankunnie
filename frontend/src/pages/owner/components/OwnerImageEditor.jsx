import { useEffect, useState } from 'react';
import Button from '@/components/common/Button';
import Alert from '@/components/common/Alert';
import RepresentativeImage from '@/components/common/RepresentativeImage';
import ImageFileInput from './ImageFileInput';
import styles from './OwnerImageEditor.module.css';

/**
 * 대표 이미지 변경을 바로 서버에 보내지 않고 저장 대기 값으로만 보관한다.
 * change: null(변경 없음) | { file } (새 이미지로 교체) | { remove: true } (삭제)
 * 실제 업로드·삭제는 화면의 저장 버튼에서 함께 처리한다.
 */
export default function OwnerImageEditor({
  imageUrl,
  change,
  onChange,
  disabled,
  kind = 'treatment',
}) {
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const file = change?.file;
  const isRemoving = Boolean(change?.remove);
  const label = kind === 'shop' ? '매장' : '시술';

  useEffect(() => {
    if (!file) {
      setPreview('');
      return undefined;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const select = (selected) => {
    if (!['image/jpeg', 'image/png'].includes(selected.type) || selected.size > 5 * 1024 * 1024) {
      setError('JPEG 또는 PNG 이미지를 5MB 이하로 선택해 주세요.');
      return;
    }
    setError('');
    onChange({ file: selected });
  };

  let caption = imageUrl ? '현재 등록된 이미지' : '기본 이미지';
  if (file) caption = '저장하면 이 이미지로 바뀝니다';
  if (isRemoving) caption = '저장하면 이미지가 삭제됩니다';

  return (
    <div className="b-stack">
      <div className={styles.editor}>
        <figure className={styles.currentImage}>
          {preview ? (
            <img src={preview} className={styles.preview} alt={`등록할 ${label} 이미지 미리보기`} />
          ) : (
            <RepresentativeImage
              key={isRemoving ? 'removed' : (imageUrl ?? 'default')}
              imageUrl={isRemoving ? null : imageUrl}
              kind={kind}
              name={label}
              className={styles.preview}
            />
          )}
          <figcaption>{caption}</figcaption>
        </figure>
        <div className={styles.editorControls}>
          <ImageFileInput
            disabled={disabled}
            hasImage={Boolean(file || (imageUrl && !isRemoving))}
            fileName={file?.name}
            onSelect={select}
          />
          <div className="b-flex">
            <Button
              type="button"
              disabled={disabled || !imageUrl || isRemoving}
              onClick={() => {
                setError('');
                onChange({ remove: true });
              }}
            >
              이미지 삭제
            </Button>
            {change && (
              <Button
                type="button"
                disabled={disabled}
                onClick={() => {
                  setError('');
                  onChange(null);
                }}
              >
                {isRemoving ? '삭제 취소' : '선택 취소'}
              </Button>
            )}
          </div>
        </div>
      </div>
      {error && (
        <Alert tone="error" role="alert">
          {error}
        </Alert>
      )}
      {change && (
        <small>아직 반영되지 않았습니다. 저장을 누르면 {isRemoving ? '삭제' : '등록'}됩니다.</small>
      )}
    </div>
  );
}
