import { Link } from 'react-router-dom';
import { money } from '@/utils/demoData';
import { areaName, earliestLabel, shopImage } from '@/utils/exploreVacancy';
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
        <span className={styles.time}>
          ◷ {earliestLabel(shop)}부터 · 빈자리 {shop.vacancyCount}개
        </span>
        <span className={styles.price}>
          <b>{money(shop.minSalePrice)}</b>부터
        </span>
      </div>
    </Link>
  );
}
