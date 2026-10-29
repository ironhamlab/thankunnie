import Panel from '@/components/common/Panel';
import RepresentativeImage from '@/pages/owner/components/RepresentativeImage';
import FavoriteButton from './FavoriteButton';
import styles from '../ShopDetailPage.module.css';

// 예약하기를 앞에 크게, 상세정보는 옆에 작게 둔다
const MENUS = [
  { key: 'reserve', title: '예약하기' },
  { key: 'info', title: '상세정보' },
];

/**
 * 매장 대표 이미지·이름과 메뉴 버튼. 메뉴를 고르면 아래에 그 내용만 보여준다.
 *
 * @param view     지금 보여주는 내용 ('reserve' | 'info')
 * @param onSelect 메뉴를 고르면 view 값을 받는 콜백
 */
export default function ShopHeader({ shop, view, onSelect }) {
  const address = [shop.address, shop.addressDetail].filter(Boolean).join(' ');

  return (
    <Panel as="section" className={styles.header}>
      <div className={styles.photo}>
        <RepresentativeImage imageUrl={shop.imageUrl} kind="shop" name={shop.shopName} />
      </div>
      <div className={styles.headerBody}>
        <div className="b-row">
          <div>
            <p className={styles.category}>{shop.categoryName}</p>
            <h1>{shop.shopName}</h1>
            <p className={styles.address}>{address}</p>
          </div>
          <FavoriteButton shopId={shop.shopId} />
        </div>
        <ul className={styles.menu} aria-label="매장 메뉴">
          {MENUS.map(({ key, title }) => (
            <li key={key}>
              <button
                type="button"
                className={view === key ? 'is-selected' : undefined}
                aria-pressed={view === key}
                onClick={() => onSelect(key)}
              >
                {title}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </Panel>
  );
}
