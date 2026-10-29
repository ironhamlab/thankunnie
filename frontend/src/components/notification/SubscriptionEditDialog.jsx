import { useState } from 'react';
import { shopTitleOf } from '@/constants/notificationExamples';
import { REGION_GROUPS } from '@/constants/regions';
import { useNotificationStore } from '@/store/useNotificationStore';
import { datePart, timePart, toKstDateTime } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './SubscriptionList.module.css';

const TIME_OPTIONS = Array.from({ length: 48 }, (_, index) => {
  const hour = String(Math.floor(index / 2)).padStart(2, '0');
  return `${hour}:${index % 2 ? '30' : '00'}`;
});

const withCurrent = (options, current) => (options.includes(current) ? options : [current, ...options]);

const toStamp = (date, time) => `${date}T${time}`;

/**
 * 구독 조건 수정. 구독 유형은 그대로 둔다.
 * 업장 구독은 매장을 바꾸지 않고 희망 시간만, 카테고리·지역 구독은 시술·지역과 희망 시간을 바꾼다.
 * localOnly 이면 서버를 부르지 않고 onSaved(request) 만 호출한다.
 */
export default function SubscriptionEditDialog({
  subscription,
  categories,
  onClose,
  onSaved,
  localOnly = false,
}) {
  const updateSubscription = useNotificationStore((state) => state.updateSubscription);
  const saving = useNotificationStore((state) => state.saving);
  const isStore = subscription.subscriptionType === 'STORE';
  const [form, setForm] = useState({
    categoryId: subscription.categoryId ?? '',
    regionCode: subscription.regionCode ?? '',
    startDate: datePart(subscription.startTime),
    startTime: timePart(subscription.startTime),
    endDate: datePart(subscription.endTime),
    endTime: timePart(subscription.endTime),
  });
  const [error, setError] = useState('');

  const change = (field) => (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));
  const invalidTime = toStamp(form.endDate, form.endTime) <= toStamp(form.startDate, form.startTime);
  const startTimes = withCurrent(TIME_OPTIONS, form.startTime);
  const endTimes = withCurrent(TIME_OPTIONS, form.endTime);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (invalidTime) return;
    // 업장 구독의 매장 ID 는 원래 값을 그대로 보낸다. 화면에서 바꾸지 않는다.
    const request = {
      shopId: isStore ? subscription.shopId : null,
      categoryId: isStore ? null : Number(form.categoryId),
      regionCode: isStore ? null : form.regionCode,
      startTime: toKstDateTime(form.startDate, form.startTime),
      endTime: toKstDateTime(form.endDate, form.endTime),
    };
    if (localOnly) {
      onSaved?.(request);
      onClose();
      return;
    }
    const { ok, error: saveError } = await updateSubscription(subscription.subscriptionId, request);
    if (!ok) {
      setError(saveError.message);
      return;
    }
    onSaved?.(request);
    onClose();
  };

  return (
    <Dialog title="조건 수정" onClose={onClose}>
      <form className={styles.form} onSubmit={handleSubmit}>
        <p className={styles.lead}>
          {isStore
            ? '매장은 그대로 두고 희망 시간만 수정합니다.'
            : '구독 유형은 바꿀 수 없습니다. 시술·지역과 희망 시간만 수정됩니다.'}
        </p>
        {isStore ? (
          <label>
            매장
            <input aria-label="매장" value={shopTitleOf(subscription)} readOnly />
          </label>
        ) : (
          <>
            <label>
              원하는 시술
              <select required value={form.categoryId} onChange={change('categoryId')}>
                <option value="" disabled>
                  시술 선택
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
          <label>
            희망 시작일
            <input required type="date" value={form.startDate} onChange={change('startDate')} />
          </label>
          <label>
            시작 시간
            <select value={form.startTime} onChange={change('startTime')}>
              {startTimes.map((time) => (
                <option key={time} value={time}>
                  {time}
                </option>
              ))}
            </select>
          </label>
          <label>
            희망 종료일
            <input required type="date" value={form.endDate} onChange={change('endDate')} />
          </label>
          <label>
            종료 시간
            <select value={form.endTime} onChange={change('endTime')}>
              {endTimes.map((time) => (
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
        {error && (
          <Alert as="p" tone="error" role="alert">
            {error}
          </Alert>
        )}
        <Button variant="primary" disabled={saving || invalidTime}>
          수정하기
        </Button>
      </form>
    </Dialog>
  );
}
