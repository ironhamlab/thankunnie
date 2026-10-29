import Panel from '@/components/common/Panel';
import styles from '../ShopDetailPage.module.css';

const DAY_LABELS = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };

/** 매장 상세정보: 주소·연락처·영업시간·소개·공지 */
export default function ShopInfoPanel({ shop }) {
  const address = [shop.address, shop.addressDetail].filter(Boolean).join(' ');

  return (
    <Panel as="section" padded className={styles.shop} aria-label="매장 상세정보">
      <h2 className="b-panel-heading">상세정보</h2>
      <dl className={styles.shopInfo}>
        <div>
          <dt>주소</dt>
          <dd>{address}</dd>
        </div>
        {shop.phone && (
          <div>
            <dt>연락처</dt>
            <dd>
              <a href={`tel:${shop.phone}`}>{shop.phone}</a>
            </dd>
          </div>
        )}
        <div>
          <dt>영업시간</dt>
          <dd>
            <ul className={styles.hours}>
              {shop.businessHours.map((hour) => (
                <li key={hour.dayOfWeek}>
                  <span>{DAY_LABELS[hour.dayOfWeek]}</span>
                  {hour.closed ? '휴무' : `${hour.openTime} ~ ${hour.closeTime}`}
                </li>
              ))}
            </ul>
          </dd>
        </div>
      </dl>
      <h3 className={styles.subheading}>매장 소개</h3>
      <p className={styles.intro}>{shop.description || '등록된 매장 소개가 없습니다.'}</p>
      {shop.notice && (
        <div className="b-highlight">
          <h3>매장 공지</h3>
          <p>{shop.notice}</p>
        </div>
      )}
    </Panel>
  );
}
