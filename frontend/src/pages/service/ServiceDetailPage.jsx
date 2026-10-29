import { useEffect } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useServiceDetailStore } from '@/store/useServiceDetailStore';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import RepresentativeImage from '@/pages/owner/components/RepresentativeImage';
import styles from './ServiceDetailPage.module.css';

const money = (value) => `${value.toLocaleString('ko-KR')}원`;
const formatTime = (value) =>
  new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value));

export default function ServiceDetailPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const startsAt = params.get('startsAt')?.replace(/\+09:00$/, '');
  const detail = useServiceDetailStore((state) => state.detail);
  const status = useServiceDetailStore((state) => state.status);
  const error = useServiceDetailStore((state) => state.error);
  const loadDetail = useServiceDetailStore((state) => state.loadDetail);
  const clearDetail = useServiceDetailStore((state) => state.clearDetail);
  // 서버는 KST LocalDateTime을 받는다. 날짜·예약 정책 검증은 서버가 담당한다.
  const hasSelection =
    /^[1-9]\d*$/.test(id ?? '') && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(startsAt ?? '');

  useEffect(() => {
    if (hasSelection) loadDetail(id, startsAt);
    return clearDetail;
  }, [id, startsAt, hasSelection, loadDetail, clearDetail]);

  if (!hasSelection) {
    return (
      <EmptyState className="b-container b-page">
        <h1>빈자리와 시작 시간을 선택해 주세요</h1>
        <p>선택한 시술과 시간으로 상세 정보를 조회합니다.</p>
        <Button as={Link} to={ROUTES.HOME}>
          빈자리 목록으로
        </Button>
      </EmptyState>
    );
  }
  if (status === 'failed') {
    const isMissing = error?.status === 404 || error?.code === 'TREATMENT_001';
    return (
      <EmptyState className="b-container b-page">
        <h1>{isMissing ? '시술을 찾을 수 없습니다' : '시술 정보를 불러오지 못했습니다'}</h1>
        <Alert tone="error" role="alert">
          {error?.message}
        </Alert>
        {!isMissing && <Button onClick={() => loadDetail(id, startsAt)}>다시 시도</Button>}
        <Button as={Link} to={ROUTES.HOME}>
          빈자리 목록으로
        </Button>
      </EmptyState>
    );
  }
  if (
    status !== 'succeeded' ||
    !detail ||
    String(detail.treatmentId) !== id ||
    detail.startsAt.slice(0, 16) !== startsAt.slice(0, 16)
  ) {
    return (
      <EmptyState className="b-container b-page" role="status">
        시술 정보를 불러오는 중입니다.
      </EmptyState>
    );
  }

  const address = [detail.address, detail.addressDetail].filter(Boolean).join(' ');
  const closedMessage =
    detail.notBookableReason === 'BOOKING_CLOSED'
      ? '예약이 마감된 시간입니다. 다른 빈자리를 선택해 주세요.'
      : '현재 예약할 수 없는 시간입니다. 다른 빈자리를 선택해 주세요.';

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <nav aria-label="현재 위치" className={styles.breadcrumb}>
        <Link to={ROUTES.HOME}>홈</Link>
        <span>›</span>
        <span>{detail.categoryName}</span>
        <span>›</span>
        <strong>
          {detail.shopName} · {detail.treatmentName}
        </strong>
      </nav>
      <div className="b-columns">
        <div className={`b-stack ${styles.content}`}>
          <section className={styles.photo}>
            <RepresentativeImage imageUrl={detail.imageUrl} name={detail.treatmentName} />
            <span className={styles.location}>{address}</span>
          </section>
          <Panel as="section" padded>
            <h1>{detail.shopName}</h1>
            <p>{address}</p>
            {detail.phone && <p>매장 연락처: {detail.phone}</p>}
            <h2>매장 소개</h2>
            <Notice className={styles.intro}>
              {detail.shopDescription || '등록된 매장 소개가 없습니다.'}
            </Notice>
            {detail.shopNotice && <Notice>{detail.shopNotice}</Notice>}
          </Panel>
          <Panel as="section" padded>
            <div className="b-row b-panel-heading">
              <h2>시술 정보</h2>
              <Badge>소요 시간 {detail.durationMinutes}분</Badge>
            </div>
            <h3>{detail.treatmentName}</h3>
            <p>{detail.treatmentDescription || '등록된 시술 설명이 없습니다.'}</p>
            {detail.treatmentType === 'MODEL' && (
              <Notice>
                <h3>시술 모델 참여 조건</h3>
                <p>{detail.modelCondition || '매장에 참여 조건을 문의해 주세요.'}</p>
              </Notice>
            )}
            {detail.photoNotice && (
              <Notice>
                <h3>촬영 안내</h3>
                <p>{detail.photoNotice}</p>
              </Notice>
            )}
            {detail.preparationNotes && (
              <Notice>
                <h3>방문 준비 사항</h3>
                <p>{detail.preparationNotes}</p>
              </Notice>
            )}
          </Panel>
          <Panel as="section" padded className={styles.slot}>
            <h2>선택한 빈자리</h2>
            <p>
              {formatTime(detail.startsAt)} ~ {formatTime(detail.endsAt)}
            </p>
            <p>예약 마감: {formatTime(detail.bookingClosesAt)}</p>
            <Badge tone={detail.bookable ? 'success' : 'neutral'}>
              {detail.bookable ? '예약 가능' : '예약 불가'}
            </Badge>
          </Panel>
        </div>
        <aside className={`b-stack ${styles.aside}`}>
          <Panel as="section" padded className="b-summary">
            <h2>빈자리 예약 요약</h2>
            <dl className="b-details">
              <div>
                <dt>정상 가격</dt>
                <dd>{money(detail.regularPrice)}</dd>
              </div>
              <div>
                <dt>할인율</dt>
                <dd>{detail.discountRate}%</dd>
              </div>
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
                {closedMessage}
              </Alert>
            )}
            <Button variant="primary" wide disabled>
              이 빈자리 예약하기
            </Button>
            <Notice>예약 기능을 준비 중입니다. 현재는 시술 상세 정보를 확인할 수 있습니다.</Notice>
            <Button as={Link} wide to={ROUTES.HOME}>
              다른 빈자리 찾기
            </Button>
          </Panel>
        </aside>
      </div>
    </div>
  );
}
