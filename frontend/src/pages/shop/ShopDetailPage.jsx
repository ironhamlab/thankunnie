import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useShopDetailStore } from '@/store/useShopDetailStore';
import { addDays, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import ShopHeader from './components/ShopHeader';
import ShopInfoPanel from './components/ShopInfoPanel';
import TreatmentPicker from './components/TreatmentPicker';
import ModelRecruitPanel from './components/ModelRecruitPanel';
import SlotPicker from './components/SlotPicker';
import BookingSummary from './components/BookingSummary';
import styles from './ShopDetailPage.module.css';

// 서버 예약 가능 범위(BookingPolicy.BOOKING_MAX_DAYS)와 같게 오늘 포함 60일까지 고른다
const BOOKING_MAX_DAYS = 60;
const BUSINESS_DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TIME_PATTERN = /^\d{2}:\d{2}$/;
const VIEWS = ['info', 'reserve'];
const MODEL_LIST_PATH = `${ROUTES.HOME}?view=list&type=MODEL`;

const businessDay = (date) => BUSINESS_DAYS[new Date(`${date}T00:00:00Z`).getUTCDay()];

/**
 * 매장 상세. 매장 이름 아래 메뉴에서 상세정보 또는 예약하기를 고르면 그 내용만 보여준다.
 * 예약은 1단계 시술 선택 → 2단계 날짜·30분 단위 시간 선택 순서로 진행한다.
 * 선택값(view, treatmentId, step, date, time)은 화면 상태로만 두며, 주소는 /shops/:shopId 로 유지한다.
 * 탐색 화면(마감 임박·시간 버튼·매장 카드)에서 ?date=&treatmentId=&startsAt= 로 들어오면 처음 선택값으로만 쓴다.
 *
 * 매장 상세는 일반 시술만, 모델 모집 상세(model, /shops/:shopId/models)는 모델 시술만 같은 흐름으로 보여준다.
 * 모델 모집은 2단계에서 고른 시술의 참여 조건·촬영 안내를 함께 보여준다.
 *
 * @param model 모델 모집 상세로 보여줄지
 */
export default function ShopDetailPage({ model = false }) {
  const { shopId } = useParams();
  const [params] = useSearchParams();
  // 주소로 넘어온 시술·날짜·시간을 처음 선택으로 잡는다. 형식·범위·판매 중 여부는 아래에서 다시 검사한다
  const initialSelection = () => {
    const startsAt = params.get('startsAt');
    const initial = { shopId };
    if (params.get('treatmentId')) initial.treatmentId = params.get('treatmentId');
    const date = startsAt ? startsAt.slice(0, 10) : params.get('date');
    if (date) initial.date = date;
    if (startsAt) initial.time = startsAt.slice(11, 16);
    return initial;
  };
  // 그 뒤 선택값은 주소에 남기지 않고 화면 상태로만 둔다
  const [selection, setSelection] = useState(initialSelection);
  // 다른 매장으로 이동하면 이전 매장의 선택은 쓰지 않는다
  const current = selection.shopId === shopId ? selection : initialSelection();
  const selected = (key) => current[key] ?? null;
  const shop = useShopDetailStore((state) => state.shop);
  const status = useShopDetailStore((state) => state.status);
  const error = useShopDetailStore((state) => state.error);
  const times = useShopDetailStore((state) => state.times);
  const loadShop = useShopDetailStore((state) => state.loadShop);
  const loadAvailableTimes = useShopDetailStore((state) => state.loadAvailableTimes);
  const clearShop = useShopDetailStore((state) => state.clearShop);
  const validShopId = /^[1-9]\d*$/.test(shopId ?? '');

  useEffect(() => {
    if (validShopId) loadShop(shopId);
    return clearShop;
  }, [shopId, validShopId, loadShop, clearShop]);

  const loaded = status === 'succeeded' && shop && String(shop.shopId) === shopId;
  const treatmentType = model ? 'MODEL' : 'NORMAL';
  const treatments = loaded
    ? shop.treatments.filter((item) => item.treatmentType === treatmentType)
    : [];
  const treatment = treatments.find(
    (item) => String(item.treatmentId) === selected('treatmentId'),
  );
  const today = todayKst();
  const lastDate = addDays(today, BOOKING_MAX_DAYS - 1);
  const isClosed = (date) => {
    const hour = shop?.businessHours.find((item) => item.dayOfWeek === businessDay(date));
    return !hour || hour.closed;
  };
  const pickedDate = selected('date');
  // 고른 날짜가 없거나 범위 밖이면 오늘부터 7일 중 첫 영업일을 고른다
  const selectedDate =
    DATE_PATTERN.test(pickedDate ?? '') && pickedDate >= today && pickedDate <= lastDate
      ? pickedDate
      : (Array.from({ length: 7 }, (_, index) => addDays(today, index)).find(
          (date) => !isClosed(date),
        ) ?? today);
  const pickedTime = selected('time');
  const selectedTime = TIME_PATTERN.test(pickedTime ?? '') ? pickedTime : null;
  // 처음 들어오면 예약하기를 먼저 보여준다
  // 모델 모집은 안내 사항 확인 체크를 해야 예약할 수 있다. 시술을 바꾸면 다시 체크한다
  const agreed = selected('agreed') === 'Y';
  const pickedView = selected('view');
  const view = VIEWS.includes(pickedView) ? pickedView : 'reserve';
  // 2단계는 예약 화면에서 시술을 고른 뒤에만 보인다. 시술 변경을 누르면 step=1 로 돌아간다
  const step = view === 'reserve' ? (treatment && selected('step') !== '1' ? 2 : 1) : null;
  const durationMinutes = step === 2 ? treatment.durationMinutes : undefined;
  const timesKey = `${shopId}-${selectedDate}-${durationMinutes}`;

  useEffect(() => {
    if (loaded && durationMinutes) loadAvailableTimes(shopId, selectedDate, durationMinutes);
  }, [loaded, shopId, selectedDate, durationMinutes, loadAvailableTimes]);

  const updateParams = (changes) => {
    const next = { ...current };
    Object.entries(changes).forEach(([key, value]) => {
      if (value == null) delete next[key];
      else next[key] = String(value);
    });
    setSelection(next);
  };

  if (!validShopId || status === 'failed') {
    const isMissing = !validShopId || error?.status === 404;
    return (
      <EmptyState className="b-container b-page">
        <h1>{isMissing ? '매장을 찾을 수 없습니다' : '매장 정보를 불러오지 못했습니다'}</h1>
        {!isMissing && error?.message && (
          <Alert tone="error" role="alert">
            {error.message}
          </Alert>
        )}
        {!isMissing && <Button onClick={() => loadShop(shopId)}>다시 시도</Button>}
        <Button as={Link} to={model ? MODEL_LIST_PATH : ROUTES.HOME}>
          {model ? '모델 모집 목록으로' : '빈자리 목록으로'}
        </Button>
      </EmptyState>
    );
  }
  if (!loaded) {
    return (
      <EmptyState className="b-container b-page" role="status">
        매장 정보를 불러오는 중입니다.
      </EmptyState>
    );
  }

  const businessHour = shop.businessHours.find(
    (item) => item.dayOfWeek === businessDay(selectedDate),
  );
  const currentTimes =
    times.key === timesKey ? times : { status: 'loading', startTimes: [], error: null };
  // 서버가 시작 가능하다고 알려준 시각만 선택된 것으로 본다
  const time =
    currentTimes.status === 'succeeded' &&
    currentTimes.startTimes.some((startTime) => startTime.slice(11, 16) === selectedTime)
      ? selectedTime
      : null;

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <nav aria-label="현재 위치" className={styles.breadcrumb}>
        <Link to={ROUTES.HOME}>홈</Link>
        <span>›</span>
        {model ? (
          <Link to={MODEL_LIST_PATH}>시술 모델 모집</Link>
        ) : (
          <span>{shop.categoryName}</span>
        )}
        <span>›</span>
        <strong>{shop.shopName}</strong>
      </nav>
      <div className={`b-columns ${step === 2 ? '' : styles.single}`}>
        <div className={`b-stack ${styles.content}`}>
          <ShopHeader shop={shop} view={view} onSelect={(key) => updateParams({ view: key })} />
          {view === 'info' && <ShopInfoPanel shop={shop} />}
          {view === 'reserve' && (
            <>
              {/* 단계 표시를 눌러 바로 이동한다. 2단계는 시술을 고른 뒤에만 갈 수 있다 */}
              <ol className={styles.steps} aria-label="예약 단계">
                <li>
                  <button
                    type="button"
                    className={step === 1 ? styles.currentStep : undefined}
                    aria-current={step === 1 ? 'step' : undefined}
                    onClick={() => updateParams({ step: 1 })}
                  >
                    1. 시술 선택
                  </button>
                </li>
                <li>
                  <button
                    type="button"
                    className={step === 2 ? styles.currentStep : undefined}
                    aria-current={step === 2 ? 'step' : undefined}
                    disabled={!treatment}
                    onClick={() => updateParams({ step: null })}
                  >
                    2. 날짜·시간 선택
                  </button>
                </li>
              </ol>
              {step === 1 && (
                <TreatmentPicker
                  treatments={treatments}
                  emptyMessage={
                    model
                      ? '지금 모집 중인 모델 시술이 없습니다.'
                      : '지금 예약할 수 있는 시술이 없습니다.'
                  }
                  selectedId={treatment?.treatmentId}
                  onSelect={(treatmentId) =>
                    updateParams({ treatmentId, step: null, time: null, agreed: null })
                  }
                />
              )}
              {step === 2 && (
                <>
                  <div className={styles.selectedTreatment}>
                    <span>
                      <small>선택한 시술</small>
                      <strong>{treatment.name}</strong> · {treatment.durationMinutes}분
                    </span>
                    <Button type="button" size="small" onClick={() => updateParams({ step: 1 })}>
                      시술 변경
                    </Button>
                  </div>
                  {model && (
                    <ModelRecruitPanel
                      treatment={treatment}
                      agreed={agreed}
                      onAgreeChange={(checked) => updateParams({ agreed: checked ? 'Y' : null })}
                    />
                  )}
                  <SlotPicker
                    firstDate={today}
                    lastDate={lastDate}
                    isClosed={isClosed}
                    selectedDate={selectedDate}
                    onSelectDate={(date) => updateParams({ date, time: null })}
                    businessHour={businessHour}
                    durationMinutes={durationMinutes}
                    times={currentTimes}
                    onRetry={() => loadAvailableTimes(shopId, selectedDate, durationMinutes)}
                    selectedTime={time}
                    onSelectTime={(value) => updateParams({ date: selectedDate, time: value })}
                  />
                </>
              )}
            </>
          )}
        </div>
        {step === 2 && (
          <aside className={`b-stack ${styles.aside}`}>
            <BookingSummary
              treatment={treatment}
              date={selectedDate}
              time={time}
              blockedMessage={
                model && !agreed ? '모델 모집 안내 사항을 확인하고 체크해 주세요.' : null
              }
            />
          </aside>
        )}
      </div>
    </div>
  );
}
