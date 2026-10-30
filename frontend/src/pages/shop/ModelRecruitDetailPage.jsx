import { useEffect, useState } from 'react';
import { Link, generatePath, useParams, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useShopDetailStore } from '@/store/useShopDetailStore';
import { addDays, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import ShopHeader from './components/ShopHeader';
import ShopInfoPanel from './components/ShopInfoPanel';
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
const ID_PATTERN = /^[1-9]\d*$/;
const MODEL_LIST_PATH = `${ROUTES.HOME}?view=list&type=MODEL`;

const businessDay = (date) => BUSINESS_DAYS[new Date(`${date}T00:00:00Z`).getUTCDay()];

/**
 * 시술 모델 모집 상세. 매장 상세와 같은 매장 화면에서 모델 시술 하나를 고정해 보여준다.
 * 예약하기 메뉴는 모집 안내(시술·참여 조건·촬영 안내) 아래에서 바로 날짜·30분 단위 시간을 고른다.
 * 선택값(view, date, time)은 화면 상태로만 두며, 주소는 /shops/:shopId/models/:treatmentId 로 유지한다.
 * 모집 목록에서 ?date=&startsAt= 로 들어오면 처음 선택값으로만 쓴다.
 */
export default function ModelRecruitDetailPage() {
  const { shopId, treatmentId } = useParams();
  const [params] = useSearchParams();
  const pageKey = `${shopId}-${treatmentId}`;
  // 주소로 넘어온 날짜·시간을 처음 선택으로 잡는다. 형식·범위는 아래에서 다시 검사한다
  const initialSelection = () => {
    const startsAt = params.get('startsAt');
    const initial = { pageKey };
    const date = startsAt ? startsAt.slice(0, 10) : params.get('date');
    if (date) initial.date = date;
    if (startsAt) initial.time = startsAt.slice(11, 16);
    return initial;
  };
  const [selection, setSelection] = useState(initialSelection);
  // 다른 모집 시술로 이동하면 이전 선택은 쓰지 않는다
  const current = selection.pageKey === pageKey ? selection : initialSelection();
  const selected = (key) => current[key] ?? null;
  const shop = useShopDetailStore((state) => state.shop);
  const status = useShopDetailStore((state) => state.status);
  const error = useShopDetailStore((state) => state.error);
  const times = useShopDetailStore((state) => state.times);
  const loadShop = useShopDetailStore((state) => state.loadShop);
  const loadAvailableTimes = useShopDetailStore((state) => state.loadAvailableTimes);
  const clearShop = useShopDetailStore((state) => state.clearShop);
  const validIds = ID_PATTERN.test(shopId ?? '') && ID_PATTERN.test(treatmentId ?? '');

  useEffect(() => {
    if (validIds) loadShop(shopId);
    return clearShop;
  }, [shopId, validIds, loadShop, clearShop]);

  const loaded = status === 'succeeded' && shop && String(shop.shopId) === shopId;
  // 판매 중인 모델 시술만 모집 상세로 보여준다
  const treatment = loaded
    ? shop.treatments.find(
        (item) => String(item.treatmentId) === treatmentId && item.treatmentType === 'MODEL',
      )
    : undefined;
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
  const pickedView = selected('view');
  const view = VIEWS.includes(pickedView) ? pickedView : 'reserve';
  const durationMinutes = view === 'reserve' ? treatment?.durationMinutes : undefined;
  const timesKey = `${shopId}-${selectedDate}-${durationMinutes}`;

  useEffect(() => {
    if (loaded && durationMinutes) loadAvailableTimes(shopId, selectedDate, durationMinutes);
  }, [loaded, shopId, selectedDate, durationMinutes, loadAvailableTimes]);

  const updateSelection = (changes) => {
    const next = { ...current };
    Object.entries(changes).forEach(([key, value]) => {
      if (value == null) delete next[key];
      else next[key] = String(value);
    });
    setSelection(next);
  };

  if (!validIds || status === 'failed' || (loaded && !treatment)) {
    const isMissing = !validIds || error?.status === 404 || loaded;
    return (
      <EmptyState className="b-container b-page">
        <h1>{isMissing ? '모집 중인 시술을 찾을 수 없습니다' : '모집 정보를 불러오지 못했습니다'}</h1>
        {!isMissing && error?.message && (
          <Alert tone="error" role="alert">
            {error.message}
          </Alert>
        )}
        {!isMissing && <Button onClick={() => loadShop(shopId)}>다시 시도</Button>}
        {/* 매장은 있지만 모집이 끝난 경우 매장의 다른 시술을 볼 수 있게 한다 */}
        {loaded && (
          <Button as={Link} to={generatePath(ROUTES.SHOP_DETAIL, { shopId })}>
            매장 보기
          </Button>
        )}
        <Button as={Link} to={MODEL_LIST_PATH}>
          모델 모집 목록으로
        </Button>
      </EmptyState>
    );
  }
  if (!loaded) {
    return (
      <EmptyState className="b-container b-page" role="status">
        모집 정보를 불러오는 중입니다.
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
        <Link to={MODEL_LIST_PATH}>시술 모델 모집</Link>
        <span>›</span>
        <Link to={generatePath(ROUTES.SHOP_DETAIL, { shopId })}>{shop.shopName}</Link>
        <span>›</span>
        <strong>{treatment.name}</strong>
      </nav>
      <div className={`b-columns ${view === 'reserve' ? '' : styles.single}`}>
        <div className={`b-stack ${styles.content}`}>
          <ShopHeader shop={shop} view={view} onSelect={(key) => updateSelection({ view: key })} />
          {view === 'info' && <ShopInfoPanel shop={shop} />}
          {view === 'reserve' && (
            <>
              <ModelRecruitPanel treatment={treatment} />
              <SlotPicker
                firstDate={today}
                lastDate={lastDate}
                isClosed={isClosed}
                selectedDate={selectedDate}
                onSelectDate={(date) => updateSelection({ date, time: null })}
                businessHour={businessHour}
                durationMinutes={durationMinutes}
                times={currentTimes}
                onRetry={() => loadAvailableTimes(shopId, selectedDate, durationMinutes)}
                selectedTime={time}
                onSelectTime={(value) => updateSelection({ date: selectedDate, time: value })}
              />
            </>
          )}
        </div>
        {view === 'reserve' && (
          <aside className={`b-stack ${styles.aside}`}>
            <BookingSummary treatment={treatment} date={selectedDate} time={time} />
          </aside>
        )}
      </div>
    </div>
  );
}
