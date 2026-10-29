import { useEffect, useState } from 'react';
import { REGION_GROUPS, regionNameOf } from '@/constants/regions';
import { useNotificationStore } from '@/store/useNotificationStore';
import { addDays, datePart, timePart, toKstDateTime, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import styles from './SubscriptionSettings.module.css';

// 알림 희망 시간은 30분 단위로만 고른다 (00:00 ~ 23:30)
const TIME_OPTIONS = Array.from({ length: 48 }, (_, index) => {
  const hour = String(Math.floor(index / 2)).padStart(2, '0');
  return `${hour}:${index % 2 ? '30' : '00'}`;
});
// 마지막 칸은 다음 날 00:00 으로 보낸다
const END_OF_DAY = '24:00';
const END_TIME_OPTIONS = [...TIME_OPTIONS.slice(1), END_OF_DAY];

const toEndDateTime = (date, time) =>
  time === END_OF_DAY ? toKstDateTime(addDays(date, 1), '00:00') : toKstDateTime(date, time);

const formatPeriod = (startTime, endTime) =>
  `${datePart(startTime)} ${timePart(startTime)} ~ ${
    datePart(endTime) === datePart(startTime) ? '' : `${datePart(endTime)} `
  }${timePart(endTime)}`;

const emptyForm = () => ({
  categoryId: '',
  regionCode: '',
  date: todayKst(),
  startTime: '10:00',
  endTime: '20:00',
});

/**
 * 빈자리 알림 조건 설정. 시술 카테고리·지역·날짜·30분 단위 시간을 정해진 값에서 골라 등록하고, 등록된 조건을 해제한다.
 */
export default function SubscriptionSettings() {
  const subscriptions = useNotificationStore((s) => s.subscriptions);
  const listStatus = useNotificationStore((s) => s.listStatus);
  const listError = useNotificationStore((s) => s.listError);
  const categories = useNotificationStore((s) => s.categories);
  const categoryStatus = useNotificationStore((s) => s.categoryStatus);
  const saving = useNotificationStore((s) => s.saving);
  const loadCategories = useNotificationStore((s) => s.loadCategories);
  const addSubscription = useNotificationStore((s) => s.addSubscription);
  const removeSubscription = useNotificationStore((s) => s.removeSubscription);
  const [form, setForm] = useState(emptyForm);
  const [result, setResult] = useState(null);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  const categoryNameOf = (categoryId) =>
    categories.find((category) => category.categoryId === categoryId)?.name ??
    `카테고리 ${categoryId}`;

  const change = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const invalidTime = form.endTime !== END_OF_DAY && form.endTime <= form.startTime;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (invalidTime) return;
    const { ok, error } = await addSubscription({
      subscriptionType: 'CATEGORY_REGION',
      shopId: null,
      categoryId: Number(form.categoryId),
      regionCode: form.regionCode,
      startTime: toKstDateTime(form.date, form.startTime),
      endTime: toEndDateTime(form.date, form.endTime),
    });
    setResult(
      ok
        ? { tone: 'success', message: '알림 조건을 추가했습니다.' }
        : { tone: 'error', message: error.message },
    );
    if (ok) setForm(emptyForm());
  };

  const handleRemove = async (subscriptionId) => {
    const { ok, error } = await removeSubscription(subscriptionId);
    setResult(
      ok
        ? { tone: 'success', message: '알림 조건을 해제했습니다.' }
        : { tone: 'error', message: error.message },
    );
  };

  return (
    <div className="b-stack">
      <Panel as="form" padded className={styles.form} onSubmit={handleSubmit}>
        <label>
          원하는 시술
          <select required value={form.categoryId} onChange={change('categoryId')}>
            <option value="" disabled>
              {categoryStatus === 'failed' ? '시술 목록을 불러오지 못했습니다' : '시술 선택'}
            </option>
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
        <label>
          날짜
          <input
            type="date"
            required
            min={todayKst()}
            value={form.date}
            onChange={change('date')}
          />
        </label>
        <div className={styles.timeRow}>
          <label>
            시작 시간
            <select value={form.startTime} onChange={change('startTime')}>
              {TIME_OPTIONS.map((time) => (
                <option key={time} value={time}>
                  {time}
                </option>
              ))}
            </select>
          </label>
          <label>
            종료 시간
            <select value={form.endTime} onChange={change('endTime')}>
              {END_TIME_OPTIONS.map((time) => (
                <option key={time} value={time}>
                  {time}
                </option>
              ))}
            </select>
          </label>
        </div>
        {invalidTime && (
          <p role="alert" className={styles.error}>
            종료 시간은 시작 시간보다 뒤여야 합니다.
          </p>
        )}
        <Button variant="primary" disabled={saving || invalidTime}>
          알림 조건 추가
        </Button>
      </Panel>

      {result && (
        <Alert as="p" tone={result.tone} role="status">
          {result.message}
        </Alert>
      )}

      {listStatus === 'loading' && <p role="status">알림 조건을 불러오는 중입니다.</p>}
      {listStatus === 'failed' && (
        <Alert as="p" tone="error">
          {listError.message}
        </Alert>
      )}
      {listStatus === 'succeeded' && !subscriptions.length && (
        <EmptyState className="b-panel">
          <h2>등록된 알림 조건이 없어요</h2>
          <p>원하는 시술·지역·시간을 골라 빈자리 알림을 받아보세요.</p>
        </EmptyState>
      )}
      {subscriptions.map((item) => (
        <Panel as="article" padded className="b-row" key={item.subscriptionId}>
          <div>
            <h3>
              {item.subscriptionType === 'STORE'
                ? `매장 알림 · 매장 ${item.shopId}`
                : `${categoryNameOf(item.categoryId)} · ${regionNameOf(item.regionCode)}`}
            </h3>
            <p className="b-muted">{formatPeriod(item.startTime, item.endTime)}</p>
          </div>
          <Button disabled={saving} onClick={() => handleRemove(item.subscriptionId)}>
            해제
          </Button>
        </Panel>
      ))}
    </div>
  );
}
