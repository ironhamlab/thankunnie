import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import { useNotificationStore } from '@/store/useNotificationStore';
import { SERVICES } from '@/utils/demoData';
import { addDays, toKstDateTime, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Asset from '@/components/common/Asset';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import formStyles from './SubscriptionCreateForm.module.css';
import styles from './FavoriteShopList.module.css';

const HOPE_DAYS = 30;
const toStamp = (date, time) => `${date}T${time}`;

const emptyTimeForm = () => {
  const startDate = todayKst();
  return {
    startDate,
    startTime: '14:00',
    endDate: addDays(startDate, 2),
    endTime: '20:00',
  };
};

/**
 * 찜한 매장 목록. 알림 받기를 누르면 그 줄 아래에 업장 구독 희망 시간을 연다.
 * 시간 입력은 카테고리·지역 조건 추가와 같은 칸을 쓴다.
 * 알림 버튼과 찜 하트를 제외한 영역을 누르면 매장 상세로 이동한다.
 */
export default function FavoriteShopList() {
  const favorites = useDesignStore((state) => state.favorites);
  const toggleFavorite = useDesignStore((state) => state.toggleFavorite);
  const saving = useNotificationStore((state) => state.saving);
  const addSubscription = useNotificationStore((state) => state.addSubscription);
  const shops = SERVICES.filter((service) => favorites.includes(service.id));
  const [openId, setOpenId] = useState(null);
  const [form, setForm] = useState(emptyTimeForm);
  const [result, setResult] = useState(null);
  const minDate = todayKst();
  const maxDate = addDays(minDate, HOPE_DAYS);

  const change = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const toggle = (shopId) => {
    setResult(null);
    if (openId === shopId) {
      setOpenId(null);
      return;
    }
    setOpenId(shopId);
    setForm(emptyTimeForm());
  };

  const startDateEmpty = !form.startDate;
  const endDateEmpty = !form.endDate;
  const startTimeEmpty = !form.startTime;
  const endTimeEmpty = !form.endTime;
  const hasFullStamp = Boolean(form.startDate && form.startTime && form.endDate && form.endTime);
  const invalidTime =
    hasFullStamp && toStamp(form.endDate, form.endTime) <= toStamp(form.startDate, form.startTime);
  const expired =
    hasFullStamp && new Date(toKstDateTime(form.endDate, form.endTime)).getTime() <= Date.now();
  const canSubmit = hasFullStamp && !invalidTime && !expired;

  const handleSubmit = async (event, shop) => {
    event.preventDefault();
    if (!canSubmit) return;
    if (typeof shop.shopId !== 'number') {
      setResult({ tone: 'error', message: '이 매장은 아직 알림 구독에 연결되지 않았습니다.' });
      return;
    }
    const { ok, error } = await addSubscription({
      subscriptionType: 'STORE',
      shopId: shop.shopId,
      categoryId: null,
      regionCode: null,
      startTime: toKstDateTime(form.startDate, form.startTime),
      endTime: toKstDateTime(form.endDate, form.endTime),
    });
    setResult(
      ok
        ? { tone: 'success', message: '알림 조건을 추가했습니다.' }
        : { tone: 'error', message: error.message },
    );
    if (ok) {
      setForm(emptyTimeForm());
      setOpenId(null);
    }
  };

  if (!shops.length) {
    return (
      <EmptyState>
        <h2>찜한 매장이 없어요</h2>
        <Link to={ROUTES.HOME}>마음에 드는 매장을 찾아보세요</Link>
      </EmptyState>
    );
  }

  return (
    <div className={styles.list}>
      {shops.map((shop) => {
        const open = openId === shop.id;
        return (
          <Panel as="article" padded className={open ? styles.cardOpen : styles.card} key={shop.id}>
            <div className={styles.row}>
              <Link
                className={styles.shopLink}
                to={generatePath(ROUTES.SERVICE_DETAIL, { id: shop.id })}
              >
                <Asset name={shop.image} alt="" className={styles.photo} />
                <div className={styles.info}>
                  <h2>{shop.shop}</h2>
                  <p>{shop.location}</p>
                </div>
              </Link>
              <div className={styles.actions}>
                <button
                  type="button"
                  className={styles.favorite}
                  aria-label="찜 해제"
                  aria-pressed="true"
                  onClick={() => toggleFavorite(shop.id)}
                >
                  ♥
                </button>
                <Button type="button" size="small" aria-expanded={open} onClick={() => toggle(shop.id)}>
                  {open ? '접기' : '알림 받기'}
                </Button>
              </div>
            </div>
            {open && (
              <form
                className={`${formStyles.form} ${styles.expand}`}
                onSubmit={(event) => handleSubmit(event, shop)}
                noValidate
              >
                <div className={formStyles.timeRow}>
                  <div className={formStyles.field}>
                    <label>
                      희망 시작일
                      <input
                        required
                        type="date"
                        min={minDate}
                        max={maxDate}
                        value={form.startDate}
                        onChange={change('startDate')}
                      />
                    </label>
                    {startDateEmpty && (
                      <p role="alert" className={formStyles.error}>
                        희망 시작일을 선택해 주세요.
                      </p>
                    )}
                  </div>
                  <div className={formStyles.field}>
                    <label>
                      시작 시간
                      <input
                        required
                        type="time"
                        step="1800"
                        value={form.startTime}
                        onChange={change('startTime')}
                      />
                    </label>
                    {startTimeEmpty && (
                      <p role="alert" className={formStyles.error}>
                        시작 시간을 선택해 주세요.
                      </p>
                    )}
                  </div>
                  <div className={formStyles.field}>
                    <label>
                      희망 종료일
                      <input
                        required
                        type="date"
                        min={minDate}
                        max={maxDate}
                        value={form.endDate}
                        onChange={change('endDate')}
                      />
                    </label>
                    {endDateEmpty && (
                      <p role="alert" className={formStyles.error}>
                        희망 종료일을 선택해 주세요.
                      </p>
                    )}
                  </div>
                  <div className={formStyles.field}>
                    <label>
                      종료 시간
                      <input
                        required
                        type="time"
                        step="1800"
                        value={form.endTime}
                        onChange={change('endTime')}
                      />
                    </label>
                    {endTimeEmpty && (
                      <p role="alert" className={formStyles.error}>
                        종료 시간을 선택해 주세요.
                      </p>
                    )}
                    {!endTimeEmpty && invalidTime && (
                      <p role="alert" className={formStyles.error}>
                        종료 시간은 시작 시간보다 뒤여야 합니다.
                      </p>
                    )}
                    {!endTimeEmpty && !invalidTime && expired && (
                      <p role="alert" className={formStyles.error}>
                        이미 지난 시간은 등록할 수 없어요.
                      </p>
                    )}
                  </div>
                </div>
                <p className={formStyles.hint}>
                  날짜는 오늘부터 {HOPE_DAYS}일 안, 시각은 30분 단위로 고를 수 있어요.
                </p>
                {result && (
                  <Alert as="p" tone={result.tone} role="status">
                    {result.message}
                  </Alert>
                )}
                <Button variant="primary" wide disabled={saving || !canSubmit}>
                  알림 조건 추가
                </Button>
              </form>
            )}
          </Panel>
        );
      })}
    </div>
  );
}
