import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getItemDetailHref } from '../../../utils/mainNavigation.js';
import {
  formatItemDate,
  formatItemPrice,
  getItemImageUrl,
  getItemStatusLabel,
  hasDiscount,
} from '../../../utils/mainItemDisplay.js';
import placeholderIcon from '../../../assets/figma/8-3-6.svg';

export default function MainItemCard({ item, detailEnabled = false }) {
  const href = detailEnabled ? getItemDetailHref(item.id) : null;
  const imageUrl = getItemImageUrl(item.imageUrl);
  const [failedImageUrl, setFailedImageUrl] = useState(null);
  const hasImage = imageUrl && imageUrl !== failedImageUrl;
  const content = (
    <>
      <div className="b-service-photo">
        {hasImage ? (
          <img
            src={imageUrl}
            alt={item.title || '시술 이미지'}
            loading="lazy"
            onError={() => setFailedImageUrl(imageUrl)}
          />
        ) : (
          <div className="main-api-image-placeholder">
            <img src={placeholderIcon} alt="" aria-hidden="true" />
            <span>이미지 준비 중</span>
          </div>
        )}
        <span
          className={`b-urgency main-api-status ${item.status === 'AVAILABLE' ? 'is-available' : ''}`}
        >
          {getItemStatusLabel(item.status)}
        </span>
      </div>
      <div className="b-service-body main-api-card-body">
        {typeof item.shopName === 'string' && item.shopName && (
          <div className="b-service-meta">{item.shopName}</div>
        )}
        <h3>{item.title || '제목 확인 필요'}</h3>
        <p className="b-service-time">시작 {formatItemDate(item.startsAt)}</p>
        <p className="main-api-closing">예약 마감 {formatItemDate(item.applicationClosesAt)}</p>
        <div className="b-row b-price-row">
          <div>
            {hasDiscount(item) && (
              <small className="b-muted">
                <del aria-label="정상가">{formatItemPrice(item.originalPrice)}</del>
              </small>
            )}
            <div className="b-price">
              <strong aria-label="판매가">{formatItemPrice(item.price)}</strong>
            </div>
          </div>
          {detailEnabled && (
            <span className="b-button b-small b-soft">
              {href ? '상세 보기 →' : '상세 정보 확인 필요'}
            </span>
          )}
        </div>
      </div>
    </>
  );
  return href ? (
    <Link className="b-service-card main-api-card" to={href}>
      {content}
    </Link>
  ) : (
    <article className="b-service-card main-api-card">{content}</article>
  );
}
