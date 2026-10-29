import { useEffect, useState } from 'react';
import { EXAMPLE_CATEGORIES } from '@/constants/notificationExamples';
import { REGION_GROUPS } from '@/constants/regions';
import { useNotificationStore } from '@/store/useNotificationStore';
import { addDays, toKstDateTime, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import InfoIcon from '@/components/icons/InfoIcon';
import styles from './SubscriptionCreateForm.module.css';

const HOPE_DAYS = 30;
const toStamp = (date, time) => `${date}T${time}`;

const emptyForm = () => {
  const startDate = todayKst();
  return {
    categoryId: '',
    regionCode: '',
    startDate,
    startTime: '14:00',
    endDate: addDays(startDate, 2),
    endTime: '20:00',
  };
};

/**
 * 카테고리·지역 알림 구독 등록. 시술·지역·희망 시작·종료를 골라 등록한다.
 * 업장 구독은 매장이 정해진 화면에서 하고, 이 폼에서는 보내지 않는다.
 * 희망 날짜는 오늘부터 30일 안만, 시각은 기본 time 입력(30분 단위)으로 고른다.
 */
export default function SubscriptionCreateForm() {
  const categories = useNotificationStore((state) => state.categories);
  const saving = useNotificationStore((state) => state.saving);
  const loadCategories = useNotificationStore((state) => state.loadCategories);
  const addSubscription = useNotificationStore((state) => state.addSubscription);
  const [form, setForm] = useState(emptyForm);
  const [result, setResult] = useState(null);
  // 서버 시술 목록이 비면 예시 카테고리로 선택지를 채운다
  const categoryOptions = categories.length ? categories : EXAMPLE_CATEGORIES;
  const minDate = todayKst();
  const maxDate = addDays(minDate, HOPE_DAYS);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  const change = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
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
  const hasSelection = Boolean(form.categoryId && form.regionCode);
  const outOfRange =
    hasFullStamp &&
    (form.startDate < minDate ||
      form.startDate > maxDate ||
      form.endDate < minDate ||
      form.endDate > maxDate);
  const canSubmit = hasFullStamp && hasSelection && !invalidTime && !expired && !outOfRange;

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!canSubmit) return;
    const { ok, error } = await addSubscription({
      subscriptionType: 'CATEGORY_REGION',
      shopId: null,
      categoryId: Number(form.categoryId),
      regionCode: form.regionCode,
      startTime: toKstDateTime(form.startDate, form.startTime),
      endTime: toKstDateTime(form.endDate, form.endTime),
    });
    setResult(
      ok
        ? { tone: 'success', message: '알림 조건을 추가했습니다.' }
        : { tone: 'error', message: error.message },
    );
    if (ok) setForm(emptyForm());
  };

  return (
    <Panel as="form" padded className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1>알림 조건 추가</h1>
      <p className={styles.lead}>시술과 지역, 희망 시간을 고르면 빈자리 문자를 받아요.</p>

      <div className={styles.grid}>
        <label>
          원하는 시술
          <select required value={form.categoryId} onChange={change('categoryId')}>
            <option value="" disabled>
              시술 선택
            </option>
            {categoryOptions.map((category) => (
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
      </div>

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

      {result && (
        <Alert as="p" tone={result.tone} role="status">
          {result.message}
        </Alert>
      )}

      <Button variant="primary" wide disabled={saving || !canSubmit}>
        알림 조건 추가
      </Button>

      <Notice className={styles.note}>
        <InfoIcon size={16} />
        이 매장만 받으려면 매장 화면에서 등록해요. 여기서는 시술과 지역을 고릅니다.
      </Notice>
    </Panel>
  );
}
