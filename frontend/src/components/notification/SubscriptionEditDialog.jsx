import { useEffect, useRef, useState } from 'react';
import { REGION_GROUPS } from '@/constants/regions';
import { useNotificationStore } from '@/store/useNotificationStore';
import { shopLabel } from '@/utils/notificationRules';
import { addDays, datePart, timePart, toKstDateTime, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './SubscriptionList.module.css';

const HOPE_DAYS = 30;
const CLOSE_MS = 280;
const toStamp = (date, time) => `${date}T${time}`;
const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * 구독 조건 수정. 구독 유형은 그대로 둔다.
 * 업장 구독은 매장을 바꾸지 않고 희망 시간만, 카테고리·지역 구독은 시술·지역과 희망 시간을 바꾼다.
 * 희망 날짜는 오늘부터 30일 안만, 시각은 기본 time 입력(30분 단위)으로 고른다.
 */
export default function SubscriptionEditDialog({ subscription, categories, onClose, onSaved }) {
  const updateSubscription = useNotificationStore((state) => state.updateSubscription);
  const shopNames = useNotificationStore((state) => state.shopNames);
  const saving = useNotificationStore((state) => state.saving);
  const isStore = subscription.subscriptionType === 'STORE';
  const minDate = todayKst();
  const maxDate = addDays(minDate, HOPE_DAYS);
  const [form, setForm] = useState({
    categoryId: subscription.categoryId ?? '',
    regionCode: subscription.regionCode ?? '',
    startDate: datePart(subscription.startTime),
    startTime: timePart(subscription.startTime),
    endDate: datePart(subscription.endTime),
    endTime: timePart(subscription.endTime),
  });
  const [error, setError] = useState('');
  const [closing, setClosing] = useState(false);
  const closeTimer = useRef(null);

  useEffect(() => () => window.clearTimeout(closeTimer.current), []);

  const requestClose = () => {
    if (closing) return;
    if (reduceMotion()) {
      onClose();
      return;
    }
    setClosing(true);
    closeTimer.current = window.setTimeout(onClose, CLOSE_MS);
  };

  const change = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));
  const startDateEmpty = !form.startDate;
  const endDateEmpty = !form.endDate;
  const startTimeEmpty = !form.startTime;
  const endTimeEmpty = !form.endTime;
  const hasFullStamp = Boolean(form.startDate && form.startTime && form.endDate && form.endTime);
  const invalidTime =
    hasFullStamp && toStamp(form.endDate, form.endTime) <= toStamp(form.startDate, form.startTime);
  const outOfRange =
    hasFullStamp &&
    (form.startDate < minDate ||
      form.startDate > maxDate ||
      form.endDate < minDate ||
      form.endDate > maxDate);
  const startChanged =
    form.startDate !== datePart(subscription.startTime) ||
    form.startTime !== timePart(subscription.startTime);
  const startPast =
    hasFullStamp &&
    startChanged &&
    new Date(toKstDateTime(form.startDate, form.startTime)).getTime() <= Date.now();
  const expired =
    hasFullStamp && new Date(toKstDateTime(form.endDate, form.endTime)).getTime() <= Date.now();
  const canSubmit = hasFullStamp && !invalidTime && !outOfRange && !expired && !startPast;

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!canSubmit) return;
    // 업장 구독의 매장 ID 는 원래 값을 그대로 보낸다. 화면에서 바꾸지 않는다.
    const request = {
      shopId: isStore ? subscription.shopId : null,
      categoryId: isStore ? null : Number(form.categoryId),
      regionCode: isStore ? null : form.regionCode,
      startTime: toKstDateTime(form.startDate, form.startTime),
      endTime: toKstDateTime(form.endDate, form.endTime),
    };
    const { ok, error: saveError } = await updateSubscription(subscription.subscriptionId, request);
    if (!ok) {
      setError(saveError.message);
      return;
    }
    onSaved?.(request);
    requestClose();
  };

  return (
    <Dialog
      className={closing ? styles.editDialogClosing : styles.editDialog}
      title="조건 수정"
      onClose={requestClose}
    >
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <p className={styles.lead}>
          {isStore
            ? '매장은 그대로 두고 희망 시간만 수정합니다.'
            : '구독 유형은 바꿀 수 없습니다. 시술·지역과 희망 시간만 수정됩니다.'}
        </p>
        {isStore ? (
          <label>
            매장
            <input
              aria-label="매장"
              value={shopLabel(subscription.shopId, shopNames[String(Number(subscription.shopId))])}
              readOnly
            />
          </label>
        ) : (
          <>
            <label>
              원하는 시술
              <select required value={form.categoryId} onChange={change('categoryId')}>
                <option value="" disabled>
                  시술 선택
                </option>
                {form.categoryId &&
                  !categories.some(
                    (category) => String(category.categoryId) === String(form.categoryId),
                  ) && (
                    <option value={form.categoryId}>{`카테고리 ${form.categoryId}`}</option>
                  )}
                {categories.map((category) => (
                  <option key={category.categoryId} value={category.categoryId}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              지역
              <select required value={form.regionCode} onChange={change('regionCode')}>
                <option value="" disabled>
                  지역 선택
                </option>
                {form.regionCode &&
                  !REGION_GROUPS.some((group) =>
                    group.regions.some((region) => region.code === form.regionCode),
                  ) && <option value={form.regionCode}>{form.regionCode}</option>}
                {REGION_GROUPS.map((group) => (
                  <optgroup key={group.label} label={group.label}>
                    {group.regions.map((region) => (
                      <option key={region.code} value={region.code}>
                        {region.name}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </label>
          </>
        )}
        <div className={styles.timeRow}>
          <div className={styles.field}>
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
              <p role="alert" className={styles.error}>
                희망 시작일을 선택해 주세요.
              </p>
            )}
            {!startDateEmpty &&
              (form.startDate < minDate || form.startDate > maxDate) && (
                <p role="alert" className={styles.error}>
                  날짜는 오늘부터 {HOPE_DAYS}일 안에서 선택해 주세요.
                </p>
              )}
          </div>
          <div className={styles.field}>
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
              <p role="alert" className={styles.error}>
                시작 시간을 선택해 주세요.
              </p>
            )}
            {!startTimeEmpty && startPast && (
              <p role="alert" className={styles.error}>
                이미 지난 시간은 선택할 수 없어요.
              </p>
            )}
          </div>
          <div className={styles.field}>
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
              <p role="alert" className={styles.error}>
                희망 종료일을 선택해 주세요.
              </p>
            )}
            {!endDateEmpty && (form.endDate < minDate || form.endDate > maxDate) && (
              <p role="alert" className={styles.error}>
                날짜는 오늘부터 {HOPE_DAYS}일 안에서 선택해 주세요.
              </p>
            )}
          </div>
          <div className={styles.field}>
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
              <p role="alert" className={styles.error}>
                종료 시간을 선택해 주세요.
              </p>
            )}
            {!endTimeEmpty && invalidTime && (
              <p role="alert" className={styles.error}>
                종료 시간은 시작 시간보다 뒤여야 합니다.
              </p>
            )}
            {!endTimeEmpty && !invalidTime && expired && (
              <p role="alert" className={styles.error}>
                이미 지난 시간은 등록할 수 없어요.
              </p>
            )}
          </div>
        </div>
        <p className={styles.hint}>날짜는 오늘부터 {HOPE_DAYS}일 안, 시각은 30분 단위로 고를 수 있어요.</p>
        {error && (
          <Alert as="p" tone="error" role="alert">
            {error}
          </Alert>
        )}
        <Button variant="primary" disabled={saving || !canSubmit}>
          수정하기
        </Button>
      </form>
    </Dialog>
  );
}
