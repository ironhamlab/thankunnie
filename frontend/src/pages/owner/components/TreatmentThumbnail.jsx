import { useState } from 'react';
import defaultImage from '@/assets/figma/12-2061-1.png';
import styles from './TreatmentThumbnail.module.css';

export default function TreatmentThumbnail({ imageUrl, name }) {
  const [failedUrl, setFailedUrl] = useState(null);
  let src = defaultImage;
  if (imageUrl && failedUrl !== imageUrl) {
    try {
      src = new URL(
        imageUrl,
        new URL(
          import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
          window.location.origin,
        ),
      ).href;
    } catch {
      // 유효하지 않은 이미지 주소도 기본 이미지로 표시한다.
    }
  }
  const isDefault = src === defaultImage;
  return (
    <img
      src={src}
      alt={isDefault ? `${name} 기본 이미지` : `${name} 대표 이미지`}
      className={styles.image}
      width="64"
      height="64"
      loading="lazy"
      onError={isDefault ? undefined : () => setFailedUrl(imageUrl)}
    />
  );
}
