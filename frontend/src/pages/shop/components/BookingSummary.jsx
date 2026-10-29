import { useEffect } from 'react';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import { useServiceDetailStore } from '@/store/useServiceDetailStore';
import styles from '../ShopDetailPage.module.css';

const money = (value) => `${value.toLocaleString('ko-KR')}원`;
// 가격 구간은 서버(PriceTier)가 정한다. 일반 구간은 따로 표시하지 않는다
const TIER_LABELS = { IMMINENT: '임박', LAST_MINUTE: '초임박 특가' };

/** 고른 시술·시각의 가격과 예약 가능 여부를 빈자리 상세(C-03)로 다시 조회해 보여준다 */
export default function BookingSummary({ treatment, date, time }) {
  const detail = useServiceDetailStore((state) => state.detail);
  const status = useServiceDetailStore((state) => state.status);
  const error = useServiceDetailStore((state) => state.error);
  const loadDetail = useServiceDetailStore((state) => state.loadDetail);
  const clearDetail = useServiceDetailStore((state) => state.clearDetail);
  const treatmentId = treatment?.treatmentId;
  const startsAt = time ? `${date}T${time}:00` : null;

  useEffect(() => {
    if (treatmentId && startsAt) loadDetail(treatmentId, startsAt);
    return clearDetail;
  }, [treatmentId, startsAt, loadDetail, clearDetail]);

  const current =
    status === 'succeeded' &&
    detail &&
    detail.treatmentId === treatmentId &&
    detail.startsAt.slice(0, 16) === startsAt?.slice(0, 16);

  return (
    <Panel as="section" padded className="b-summary">
      <h2>예약 요약</h2>
      {!treatment && <Notice>시술을 먼저 선택해 주세요.</Notice>}
      {treatment && !startsAt && <Notice>날짜와 시작 시간을 선택해 주세요.</Notice>}
      {startsAt && status === 'failed' && (
        <>
          <Alert tone="error" role="alert">
            {error?.message}
          </Alert>
          <Button type="button" onClick={() => loadDetail(treatmentId, startsAt)}>
            다시 시도
          </Button>
        </>
      )}
      {startsAt && status !== 'failed' && !current && (
        <Notice role="status">가격을 확인하는 중입니다.</Notice>
      )}
      {current && (
        <>
          <dl className="b-details">
            <div>
              <dt>시술</dt>
              <dd>{detail.treatmentName}</dd>
            </div>
            <div>
              <dt>일시</dt>
              <dd>
                {date} {time}
              </dd>
            </div>
            <div>
              <dt>정상 가격</dt>
              <dd>{money(detail.regularPrice)}</dd>
            </div>
            {detail.salePrice < detail.regularPrice && (
              <div>
                <dt>
                  할인
                  {TIER_LABELS[detail.priceTier] && (
                    <Badge className={styles.tier}>{TIER_LABELS[detail.priceTier]}</Badge>
                  )}
                </dt>
                <dd>
                  -{money(detail.regularPrice - detail.salePrice)} ({detail.discountRate}%)
                </dd>
              </div>
            )}
            <div>
              <dt>최종 예약 금액</dt>
              <dd className="b-final-price">{money(detail.salePrice)}</dd>
            </div>
            <div>
              <dt>예약금</dt>
              <dd>{money(detail.depositAmount)}</dd>
            </div>
            <div>
              <dt>매장 방문 현장 잔금</dt>
              <dd>{money(detail.balanceAmount)}</dd>
            </div>
          </dl>
          <Notice>가격과 예약 가능 여부는 조회 시점 기준이며 예약 시 다시 확인합니다.</Notice>
          {!detail.bookable && (
            <Alert tone="error" role="alert">
              {detail.notBookableReason === 'BOOKING_CLOSED'
                ? '예약이 마감된 시간입니다. 다른 시간을 선택해 주세요.'
                : '현재 예약할 수 없는 시간입니다. 다른 시간을 선택해 주세요.'}
            </Alert>
          )}
        </>
      )}
      <Button variant="primary" wide disabled>
        예약하기
      </Button>
      <Notice>예약 기능을 준비 중입니다. 현재는 시술과 시간을 확인할 수 있습니다.</Notice>
    </Panel>
  );
}
