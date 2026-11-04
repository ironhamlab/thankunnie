import { useState } from 'react';
import defaultShop from '@/assets/images/default-shop.svg';
import defaultTreatment from '@/assets/images/default-treatment.svg';

export default function RepresentativeImage({ imageUrl, kind = 'treatment', name = '', ...props }) {
  const [failedUrl, setFailedUrl] = useState(null);
  const fallback = kind === 'shop' ? defaultShop : defaultTreatment;
  let src = fallback;
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
      // 잘못된 이미지 주소도 기본 일러스트로 표시한다.
    }
  }
  const isDefault = src === fallback;
  return (
    <img
      {...props}
      src={src}
      alt={`${name} ${isDefault ? '기본 일러스트' : '대표 이미지'}`.trim()}
      onError={isDefault ? undefined : () => setFailedUrl(imageUrl)}
    />
  );
}
