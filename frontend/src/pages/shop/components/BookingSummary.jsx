import { useEffect } from 'react';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import HoldReplaceDialog from '@/components/reservation/HoldReplaceDialog';
import QueueWaitingDialog from '@/components/reservation/QueueWaitingDialog';
import { useVacancyDetailStore } from '@/store/useVacancyDetailStore';
import useReserve from '@/hooks/useReserve';
import { formatMoney } from '@/utils/reservationFormat';
import styles from '../ShopDetailPage.module.css';

// 가격 구간은 서버(PriceTier)가 정한다. 일반 구간은 따로 표시하지 않는다
const TIER_LABELS = { IMMINENT: '임박', LAST_MINUTE: '초임박 특가' };

/** 고른 시술·시각의 가격과 예약 가능 여부를 빈자리 상세(C-03)로 다시 조회해 보여준다 */
export default function BookingSummary({ treatment, date, time }) {
  const detail = useVacancyDetailStore((state) => state.detail);
  const status = useVacancyDetailStore((state) => state.status);
  const error = useVacancyDetailStore((state) => state.error);
  const loadDetail = useVacancyDetailStore((state) => state.loadDetail);
  const clearDetail = useVacancyDetailStore((state) => state.clearDetail);
  const {
    reserve,
    loading,
    error: reserveError,
    pendingHold,
    replaceHold,
    cancelReplace,
    queue,
    cancelQueue,
  } = useReserve();
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
  // 다른 고객이 결제 중인 시간. 바로 예약할 수는 없지만 대기열에 서서 결제 없이 풀리면 이어서 예약한다
  const waitable = current && detail.notBookableReason === 'HELD';

  return (
    <Panel as="section" padded className="b-summary">
      <h2>예약 요약</h2>
      {!treatment && <Notice>시술을 먼저 선택해 주세요.</Notice>}
      {treatment && !startsAt && (
        <>
          <dl className="b-details">
            <div>
              <dt>시술</dt>
              <dd>{treatment.name}</dd>
            </div>
          </dl>
          <Notice>날짜와 시작 시간을 선택해 주세요.</Notice>
        </>
      )}
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
              <dd>{formatMoney(detail.regularPrice)}</dd>
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
                  -{formatMoney(detail.regularPrice - detail.salePrice)} ({detail.discountRate}%)
                </dd>
              </div>
            )}
            <div>
              <dt>최종 예약 금액</dt>
              <dd className="b-final-price">{formatMoney(detail.salePrice)}</dd>
            </div>
            <div>
              <dt>예약금</dt>
              <dd>{formatMoney(detail.depositAmount)}</dd>
            </div>
            <div>
              <dt>매장 방문 현장 잔금</dt>
              <dd>{formatMoney(detail.balanceAmount)}</dd>
            </div>
          </dl>
          <Notice>가격과 예약 가능 여부는 조회 시점 기준이며 예약 시 다시 확인합니다.</Notice>
          {waitable && (
            <Notice role="status">
              다른 분이 결제 중인 시간이에요. 대기하면 결제하지 않을 때 순서대로 예약할 수 있어요.
            </Notice>
          )}
          {!detail.bookable && !waitable && (
            <Alert tone="error" role="alert">
              {detail.notBookableReason === 'BOOKING_CLOSED'
                ? '예약이 마감된 시간입니다. 다른 시간을 선택해 주세요.'
                : '현재 예약할 수 없는 시간입니다. 다른 시간을 선택해 주세요.'}
            </Alert>
          )}
        </>
      )}
      {reserveError && (
        <Alert tone="error" role="alert">
          {reserveError}
        </Alert>
      )}
      {/* 조회한 가격이 지금 고른 시술·시각의 것이고 예약 가능하거나 대기할 수 있을 때만 누를 수 있다. 확정 여부는 서버가 다시 판단한다 */}
      <Button
        variant="primary"
        wide
        disabled={!current || !(detail.bookable || waitable) || loading}
        onClick={() => reserve(treatmentId, date, time)}
      >
        {loading ? '예약하는 중...' : waitable ? '대기하기' : '예약하기'}
      </Button>
      {queue && (
        <QueueWaitingDialog
          aheadCount={queue.aheadCount}
          paying={queue.paying}
          onCancel={cancelQueue}
        />
      )}
      {pendingHold && (
        <HoldReplaceDialog
          hold={pendingHold}
          loading={loading}
          onReplace={replaceHold}
          onClose={cancelReplace}
        />
      )}
    </Panel>
  );
}
