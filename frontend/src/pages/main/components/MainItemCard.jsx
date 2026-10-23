import { Link } from 'react-router-dom';
import { getItemDetailHref } from '../../../utils/mainNavigation.js';
import {
  formatItemDate,
  formatItemPrice,
  getItemStatusLabel,
  hasDiscount,
} from '../../../utils/mainItemDisplay.js';

export default function MainItemCard({ item }) {
  const href = getItemDetailHref(item.id);
  const content = (
    <>
      <span className="main-item-status">{getItemStatusLabel(item.status)}</span>
      <h3>{item.title || '제목 확인 필요'}</h3>
      {item.shopName && <p>{item.shopName}</p>}
      <p>시작 {formatItemDate(item.startsAt)}</p>
      {item.applicationClosesAt && <p>예약 마감 {formatItemDate(item.applicationClosesAt)}</p>}
      <div className="main-item-price">
        {hasDiscount(item) && <del aria-label="정상가">{formatItemPrice(item.originalPrice)}</del>}
        <strong aria-label="판매가">{formatItemPrice(item.price)}</strong>
      </div>
      <span>{href ? '상세 보기 →' : '상세 정보 확인 필요'}</span>
    </>
  );
  return href ? (
    <Link className="main-item-card" to={href}>
      {content}
    </Link>
  ) : (
    <article className="main-item-card">{content}</article>
  );
}
