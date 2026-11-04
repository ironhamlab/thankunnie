import { Link } from 'react-router-dom';
import { formatMoney } from '@/utils/reservationFormat';
import { areaName, shopImage, vacancySummary } from '@/utils/exploreVacancy';
import styles from './ShopCard.module.css';

/**
 * 홈 분야 탭의 작은 매장 카드. shop 은 매장 요약(C-02) 한 건이다.
 */
export default function ShopCard({ shop, to }) {
  return (
    <Link className={styles.root} to={to}>
      <div className={styles.photo}>
        <img src={shopImage(shop.imageUrl)} alt="" />
        {shop.maxDiscountRate > 0 && (
          <span className={styles.discount}>최대 {shop.maxDiscountRate}%↓</span>
        )}
      </div>
      <div className={styles.body}>
        <strong className={styles.name}>{shop.shopName}</strong>
        <span className={styles.area}>⌖ {areaName(shop.address)}</span>
        <span className={styles.time}>◷ {vacancySummary(shop)}</span>
        <span className={styles.price}>
          <b>{formatMoney(shop.minSalePrice)}</b>부터
        </span>
      </div>
    </Link>
  );
}
