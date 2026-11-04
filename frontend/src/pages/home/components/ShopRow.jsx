import { Link } from 'react-router-dom';
import { formatMoney } from '@/utils/reservationFormat';
import {
  areaName,
  earliestLabel,
  groupByTreatment,
  shopImage,
  shopPagePath,
} from '@/utils/exploreVacancy';
import { datePart } from '@/utils/scheduleDate';
import SproutIcon from '@/components/icons/SproutIcon';
import styles from './ShopRow.module.css';

// 전체 날짜 보기는 60일 치가 섞여 길어지므로 빈자리가 있는 가장 빠른 날짜 이만큼만 펼쳐 보여준다
const SHOWN_DAYS = 2;

// 옆 지도가 있는 목록(onLocate)은 카드를 눌러 지도에 위치를 띄우고, 없으면 매장 페이지로 간다
function Card({ to, onLocate, located, children }) {
  if (!onLocate) {
    return (
      <Link className={styles.shopLink} to={to}>
        {children}
      </Link>
    );
  }
  return (
    <button
      type="button"
      className={`${styles.shopLink} ${styles.locateCard}`}
      aria-pressed={located}
      onClick={onLocate}
    >
      {children}
    </button>
  );
}

/**
 * 검색 화면의 한 줄 매장 카드. 매장을 누르면 매장 페이지로 가고(onLocate 가 있으면 옆 지도에 위치를 보여주고),
 * 오른쪽 '시간 보기'를 누르면 아래가 펼쳐지며 시술별 시작 시각 버튼을 보여준다. 시각을 누르면 그 시간이 골라진 매장 페이지로 간다.
 *
 * @param shop     매장 요약(C-02) 한 건
 * @param expanded 펼쳐져 있는지
 * @param detail   펼친 매장의 빈자리 { status, vacancies }. 아직 안 받았으면 undefined
 * @param onToggle 펼치기·접기
 * @param onRetry  빈자리를 다시 받기
 * @param showTimes 시간 보기 버튼을 보일지
 * @param onLocate  있으면 카드를 눌렀을 때 매장 페이지로 가지 않고 옆 지도에서 이 매장을 가까이 보여준다.
 *                  매장 페이지는 시간 버튼이나 옆 지도의 매장 보기로 간다
 * @param located   옆 지도에서 지금 이 매장을 보여주는 중인지
 * @param distance  내 위치에서 매장까지 거리 ("1.2km"). 위치를 모르면 undefined 라 보이지 않는다
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
  onLocate,
  located = false,
  distance,
}) {
  const area = (
    <span className={styles.area}>
      ⌖ {areaName(shop.address)}
      {distance && ` · ${distance}`}
    </span>
  );
  const locate = onLocate && (() => onLocate(shop));
  const panelId = `shop-${shop.shopId}-vacancies`;
  // 여러 날짜의 빈자리가 섞이면 앞쪽 날짜만 날짜별로 묶고, 나머지는 매장 페이지에서 고르게 한다
  const vacancies = detail?.vacancies ?? [];
  const dates = [...new Set(vacancies.map((vacancy) => datePart(vacancy.startsAt)))].sort();
  const multiDay = dates.length > 1;
  const shownDates = dates.slice(0, SHOWN_DAYS);
  const shownVacancies = vacancies.filter((vacancy) =>
    shownDates.includes(datePart(vacancy.startsAt)),
  );
  const nextDate = dates[SHOWN_DAYS];
  // 받은 빈자리가 개수 제한에 걸려 잘렸을 수도 있으므로 매장 전체 빈자리 수와도 비교한다.
  // 하루치만 받았어도 잘렸을 수 있으므로 날짜 수와 상관없이 안내한다
  const hasMore = Boolean(nextDate) || shop.vacancyCount > shownVacancies.length;
  // 다음 날짜가 없으면 잘린 빈자리는 마지막으로 받은 날짜부터 이어진다
  const moreDate = nextDate ?? dates.at(-1);
  const dayOf = (date) => vacancies.find((vacancy) => datePart(vacancy.startsAt) === date).day;
  // 전체 매장 목록에서 온 매장은 빈자리 요약이 없다. 매장 상세에서 날짜를 골라 다시 찾아보게 한다
  if (shop.earliestStartsAt == null) {
    return (
      <article className={`${styles.root} ${styles.noVacancy} ${located ? styles.located : ''}`}>
        <div className={styles.summary}>
          <Card to={shopPagePath(shop.shopId)} onLocate={locate} located={located}>
            <img className={styles.thumb} src={shopImage(shop.imageUrl)} alt="" />
            <span className={styles.info}>
              <strong>{shop.shopName}</strong>
              {area}
              <span className={styles.none}>지금은 빈자리가 없어요</span>
            </span>
          </Card>
        </div>
      </article>
    );
  }
  return (
    <article
      className={`${styles.root} ${expanded ? styles.expanded : ''} ${located ? styles.located : ''}`}
    >
      <div className={styles.summary}>
        <Card
          to={shopPagePath(shop.shopId, {
            date: datePart(shop.earliestStartsAt),
            model: model || shop.modelOnly,
          })}
          onLocate={locate}
          located={located}
        >
          <img className={styles.thumb} src={shopImage(shop.imageUrl)} alt="" />
          <span className={styles.info}>
            <strong>{shop.shopName}</strong>
            {area}
            <span className={styles.time}>
              {/* 줄이 바뀌어도 "빈자리 N개"는 떨어지지 않게 붙는 공백(NBSP)을 쓴다 */}◷{' '}
              {earliestLabel(shop)}부터 · 빈자리{' '}
              {shop.vacancyCount}개
            </span>
          </span>
          <span className={styles.price}>
            {shop.maxDiscountRate > 0 && <em>최대 {shop.maxDiscountRate}%↓</em>}
            <b>{formatMoney(shop.minSalePrice)}~</b>
          </span>
        </Card>
        {showTimes && (
          <button
            type="button"
            className={styles.toggle}
            aria-expanded={expanded}
            aria-controls={panelId}
            onClick={onToggle}
          >
            {/* 펼칠 때 글자 폭이 바뀌면 왼쪽 가격이 밀리므로 두 문구의 글자 수를 맞춘다 (옆 지도 패널과 같은 문구) */}
            {expanded ? '시간 접기' : '시간 보기'}
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
          <span className={styles.modelLinkLabel}>
            <SproutIcon size={16} />
            초보 시술 모집 빈자리 {shop.modelVacancyCount}개
          </span>
          <b>{formatMoney(shop.modelMinSalePrice)}~</b>
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
            <>
              {groupByTreatment(shownVacancies).map((treatment) => (
                // 일반 시술과 섞여 있으면 초보 시술을 색과 표시로 구분한다. 초보 시술 모집 목록은 전부 초보 시술이라 구분하지 않는다
                <div
                  className={`${styles.treatment} ${treatment.isModel && !model ? styles.modelTreatment : ''}`}
                  key={treatment.treatmentId}
                >
                  <div className={styles.treatmentInfo}>
                    {treatment.isModel && !model && (
                      <span className={styles.modelBadge}>
                        <SproutIcon size={16} />
                        초보 시술
                      </span>
                    )}
                    <strong>{treatment.name}</strong>
                    <span>
                      {treatment.duration}분 · {formatMoney(treatment.price)}
                      {treatment.discount > 0 && <em> {treatment.discount}%↓</em>}
                    </span>
                  </div>
                  {multiDay ? (
                    shownDates.map((date) => {
                      const slots = treatment.slots.filter(
                        (slot) => datePart(slot.startsAt) === date,
                      );
                      return (
                        slots.length > 0 && (
                          <div className={styles.dayRow} key={date}>
                            <span className={styles.dayLabel}>{dayOf(date)}</span>
                            <SlotLinks treatment={treatment} slots={slots} />
                          </div>
                        )
                      );
                    })
                  ) : (
                    <SlotLinks treatment={treatment} slots={treatment.slots} />
                  )}
                </div>
              ))}
              {hasMore && (
                <p className={styles.message}>
                  {nextDate ? `${dayOf(nextDate)}부터는` : '다른 시간은'}{' '}
                  <Link
                    to={shopPagePath(shop.shopId, {
                      date: moreDate,
                      model: model || shop.modelOnly,
                    })}
                  >
                    매장 페이지
                  </Link>
                  에서 골라요
                </p>
              )}
            </>
          )}
        </div>
      )}
    </article>
  );
}

/** 한 시술의 시작 시각 버튼들. 누르면 그 시간이 골라진 매장 페이지로 간다 */
function SlotLinks({ treatment, slots }) {
  return (
    <div className={styles.slots}>
      {slots.map((slot) => (
        <Link
          key={slot.id}
          to={slot.shopPath}
          className={slot.urgency !== '일반 빈자리' ? styles.urgent : ''}
          aria-label={`${treatment.name} ${slot.day} ${slot.time} ${formatMoney(slot.price)}`}
        >
          {slot.time}
        </Link>
      ))}
    </div>
  );
}
