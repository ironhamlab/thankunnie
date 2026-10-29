import { useEffect } from 'react';
import { generatePath, Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useVacancyDetailStore } from '@/store/useVacancyDetailStore';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';

/**
 * 이전 시술 상세 주소(/services/:treatmentId?startsAt=...).
 * 빈자리 상세(C-03)로 시술의 매장을 찾은 뒤 같은 시술·날짜·시간을 고른 매장 상세로 이동한다.
 */
export default function LegacyServiceDetailRedirect() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const startsAt = params.get('startsAt')?.replace(/\+09:00$/, '');
  const detail = useVacancyDetailStore((state) => state.detail);
  const status = useVacancyDetailStore((state) => state.status);
  const error = useVacancyDetailStore((state) => state.error);
  const loadDetail = useVacancyDetailStore((state) => state.loadDetail);
  const clearDetail = useVacancyDetailStore((state) => state.clearDetail);
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
  if (status === 'succeeded' && detail && String(detail.treatmentId) === id) {
    // 매장 상세는 선택값을 주소에 두지 않으므로 이동 state 로 처음 선택을 넘긴다
    const selection = {
      treatmentId: id,
      date: startsAt.slice(0, 10),
      time: startsAt.slice(11, 16),
    };
    return (
      <Navigate
        to={generatePath(ROUTES.SHOP_DETAIL, { shopId: detail.shopId })}
        state={{ selection }}
        replace
      />
    );
  }
  return (
    <EmptyState className="b-container b-page" role="status">
      시술 정보를 불러오는 중입니다.
    </EmptyState>
  );
}
