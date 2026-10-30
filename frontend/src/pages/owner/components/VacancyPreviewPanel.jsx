import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { formatMoney } from '@/utils/reservationFormat';
import { discountRate, priceTierAt, startTimesWithin } from '@/utils/vacancyPreview';
import Badge from '@/components/common/Badge';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import styles from './VacancyPreviewPanel.module.css';

const TIER_BADGES = {
  NORMAL: null,
  IMMINENT: ['임박', ''],
  LAST_MINUTE: ['초임박', 'b-pink'],
};

/**
 * 빈자리 열기 미리보기. 연 시간대에 어떤 시술이 몇 시에, 얼마에 고객 빈자리 목록에 보이는지 화면에서 미리 계산한다.
 */
export default function VacancyPreviewPanel({
  treatments,
  status,
  error,
  date,
  startTime,
  endTime,
  blockedTimes,
}) {
  const items = treatments.map((treatment) => {
    const times = startTimesWithin(startTime, endTime, treatment.durationMinutes, blockedTimes);
    const tier = times.length ? priceTierAt(date, times[0]) : null;
    return { treatment, times, tier };
  });
  const listed = items.filter((item) => item.times.length);

  return (
    <Panel as="section" padded className={styles.root}>
      <div className="b-row b-panel-heading">
        <h2>고객에게 이렇게 보여요</h2>
        {status === 'succeeded' && <Badge>{listed.length}개 시술 노출</Badge>}
      </div>
      {status === 'loading' && <p role="status">시술을 불러오는 중입니다.</p>}
      {status === 'failed' && <p role="alert">{error?.message ?? '시술을 불러오지 못했습니다.'}</p>}
      {status === 'succeeded' && !treatments.length && (
        <EmptyState as="p">
          판매 중인 시술이 없습니다. <Link to={ROUTES.OWNER_MENUS}>시술 관리</Link>에서 먼저 등록해
          주세요.
        </EmptyState>
      )}
      <ul className={styles.list}>
        {items.map(({ treatment, times, tier }) => {
          const salePrice = tier === 'LAST_MINUTE' ? treatment.lastMinutePrice : treatment.price;
          const badge = tier && TIER_BADGES[tier];
          return (
            <li
              key={treatment.treatmentId}
              className={times.length ? styles.item : `${styles.item} ${styles.hidden}`}
            >
              <div className="b-row">
                <strong>{treatment.name}</strong>
                {badge && <Badge className={badge[1]}>{badge[0]}</Badge>}
              </div>
              {times.length ? (
                <>
                  <p className={styles.price}>
                    <del>{formatMoney(treatment.regularPrice)}</del>{' '}
                    <strong>{formatMoney(salePrice)}</strong>{' '}
                    <span className="b-pink">
                      {discountRate(treatment.regularPrice, salePrice)}%
                    </span>
                  </p>
                  <small>
                    소요 {treatment.durationMinutes}분 · 시작 {times[0]}
                    {times.length > 1 && ` ~ ${times[times.length - 1]}`} ({times.length}개 시각)
                  </small>
                  {tier !== 'LAST_MINUTE' && (
                    <small>
                      시작 3시간 전부터 초임박가 {formatMoney(treatment.lastMinutePrice)}
                    </small>
                  )}
                </>
              ) : (
                <small>
                  소요 {treatment.durationMinutes}분 · 이 시간대에 비어 있는 시간이 모자라 보이지
                  않아요
                </small>
              )}
            </li>
          );
        })}
      </ul>
      <Notice className="b-help">
        가격은 시술 관리의 판매가·초임박가를 따르고, 고객이 예약을 누른 시점에 확정됩니다. 이미
        예약됐거나 닫은 시간은 빠집니다.
      </Notice>
    </Panel>
  );
}
