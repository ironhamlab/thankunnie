import { Link } from 'react-router-dom';
import { money } from '@/utils/demoData';
import {
  areaName,
  earliestLabel,
  groupByTreatment,
  shopImage,
  shopPagePath,
} from '@/utils/exploreVacancy';
import { datePart } from '@/utils/scheduleDate';
import styles from './ShopRow.module.css';

/**
 * 검색 화면의 한 줄 매장 카드. 매장을 누르면 매장 페이지로 가고, 오른쪽 '시간 보기'를 누르면
 * 아래가 펼쳐지며 시술별 시작 시각 버튼을 보여준다. 시각을 누르면 그 시간이 골라진 매장 페이지로 간다.
 *
 * @param shop     매장 요약(C-02) 한 건
 * @param expanded 펼쳐져 있는지
 * @param detail   펼친 매장의 빈자리 { status, vacancies }. 아직 안 받았으면 undefined
 * @param onToggle 펼치기·접기
 * @param onRetry  빈자리를 다시 받기
 * @param showTimes 시간 보기 버튼을 보일지. 전체 날짜 보기는 날짜가 섞여 매장 상세에서 날짜를 고른다
 * @param model    초보 시술 모집 목록이면 매장 대신 초보 시술 모집 상세로 보낸다. 초보 시술 빈자리만 있는 매장(shop.modelOnly)도 그렇다.
 *                 일반·초보 시술이 섞인 매장은 카드 아래에 초보 시술 모집 상세 링크를 따로 둔다
 */
export default function ShopRow({
  shop,
  expanded,
  detail,
  onToggle,
  onRetry,
  model,
  showTimes = true,
}) {
  const panelId = `shop-${shop.shopId}-vacancies`;
  return (
    <article className={`${styles.root} ${expanded ? styles.expanded : ''}`}>
      <div className={styles.summary}>
        <Link
          className={styles.shopLink}
          to={shopPagePath(shop.shopId, {
            date: datePart(shop.earliestStartsAt),
            model: model || shop.modelOnly,
          })}
        >
          <img className={styles.thumb} src={shopImage(shop.imageUrl)} alt="" />
          <span className={styles.info}>
            <strong>{shop.shopName}</strong>
            <span className={styles.area}>⌖ {areaName(shop.address)}</span>
            <span className={styles.time}>
              ◷ {earliestLabel(shop)}부터 · 빈자리 {shop.vacancyCount}개
            </span>
          </span>
          <span className={styles.price}>
            {shop.maxDiscountRate > 0 && <em>최대 {shop.maxDiscountRate}%↓</em>}
            <b>{money(shop.minSalePrice)}~</b>
          </span>
        </Link>
        {showTimes && (
          <button
            type="button"
            className={styles.toggle}
            aria-expanded={expanded}
            aria-controls={panelId}
            onClick={onToggle}
          >
            {expanded ? '접기' : '시간 보기'}
            <span aria-hidden="true">{expanded ? '▴' : '▾'}</span>
          </button>
        )}
      </div>
      {/* 일반·초보 시술이 섞인 매장은 위 카드가 매장 상세(일반 시술)로 가므로 초보 시술 빈자리는 초보 시술 모집 상세로 따로 보낸다 */}
      {!model && !shop.modelOnly && shop.modelVacancyCount > 0 && (
        <Link
          className={styles.modelLink}
          to={shopPagePath(shop.shopId, { date: datePart(shop.earliestStartsAt), model: true })}
        >
          <span>✦ 초보 시술 모집 빈자리 {shop.modelVacancyCount}개</span>
          <b>{money(shop.modelMinSalePrice)}~</b>
        </Link>
      )}
      {expanded && (
        <div id={panelId} className={styles.panel}>
          {!detail || detail.status === 'loading' ? (
            <p className={styles.message}>빈자리를 불러오는 중이에요…</p>
          ) : detail.status === 'failed' ? (
            <p className={styles.message} role="alert">
              빈자리를 불러오지 못했어요.{' '}
              <button type="button" onClick={onRetry}>
                다시 시도
              </button>
            </p>
          ) : detail.vacancies.length === 0 ? (
            <p className={styles.message}>방금 빈자리가 모두 마감됐어요.</p>
          ) : (
            groupByTreatment(detail.vacancies).map((treatment) => (
              <div className={styles.treatment} key={treatment.treatmentId}>
                <div className={styles.treatmentInfo}>
                  <strong>{treatment.name}</strong>
                  <span>
                    {treatment.duration}분 · {money(treatment.price)}
                    {treatment.discount > 0 && <em> {treatment.discount}%↓</em>}
                  </span>
                </div>
                <div className={styles.slots}>
                  {treatment.slots.map((slot) => (
                    <Link
                      key={slot.id}
                      to={slot.shopPath}
                      className={slot.urgency !== '일반 빈자리' ? styles.urgent : ''}
                      aria-label={`${treatment.name} ${slot.day} ${slot.time} ${money(slot.price)}`}
                    >
                      {slot.time}
                    </Link>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </article>
  );
}
