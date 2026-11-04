/**
 * @file ui:time-picker
 * @requires @seed-design/react@^3.0.0
 * @requires @seed-design/css@^3.0.0
 * @requires clsx@^2.1.1
 **/

import { timePicker } from '@seed-design/css/recipes/time-picker';
import { useTimePicker } from '@seed-design/react';
import clsx from 'clsx';
import * as React from 'react';
import { WheelPicker } from './wheel-picker';

const ITEM_SIZE = 44;
const VISIBLE_ITEM_COUNT = 5;
const DEFAULT_ARIA_LABELS = {
  ko: {
    root: '시간 선택',
    period: '오전/오후',
    hour: '시',
    minute: '분',
  },
  en: {
    root: 'Select time',
    period: 'AM/PM',
    hour: 'Hour',
    minute: 'Minute',
  },
  ja: {
    root: '時刻を選択',
    period: '午前/午後',
    hour: '時',
    minute: '分',
  },
};

function getDefaultAriaLabels(locale) {
  const [canonicalLocale = 'ko-KR'] = Intl.getCanonicalLocales(locale ?? 'ko-KR');
  const language = new Intl.Locale(canonicalLocale).language;

  if (language === 'ko') return DEFAULT_ARIA_LABELS.ko;
  if (language === 'ja') return DEFAULT_ARIA_LABELS.ja;
  return DEFAULT_ARIA_LABELS.en;
}

const classNames = timePicker();

const columnClassNames = {
  period: classNames.periodColumn,
  hour: classNames.hourColumn,
  minute: classNames.minuteColumn,
};

/**
 * 휠을 스크롤하여 오전·오후, 시, 분을 선택하는 12시간제 시간 선택 컴포넌트입니다.
 *
 * 값은 오전·오후를 별도 필드로 나누지 않고 24시간 형식의 TimePickerValue로 주고받습니다.
 */
export const TimePicker = React.forwardRef(function TimePicker(
  {
    value,
    defaultValue,
    onValueChange,
    minuteStep,
    locale,
    disabled,
    periodAriaLabel,
    hourAriaLabel,
    minuteAriaLabel,
    className,
    'aria-label': ariaLabel,
    'aria-labelledby': ariaLabelledby,
    ...props
  },
  ref,
) {
  const api = useTimePicker({
    value,
    defaultValue,
    onValueChange,
    minuteStep,
    locale,
    disabled,
  });
  const defaultAriaLabels = getDefaultAriaLabels(locale);
  const ariaLabels = {
    period: periodAriaLabel ?? defaultAriaLabels.period,
    hour: hourAriaLabel ?? defaultAriaLabels.hour,
    minute: minuteAriaLabel ?? defaultAriaLabels.minute,
  };

  const columns = api.columnOrder.map((type) => {
    const column = api.columns[type];

    return {
      id: type,
      'aria-label': ariaLabels[type],
      className: columnClassNames[type],
      options: column.options,
      value: column.value,
      onValueChange: column.onValueChange,
      loop: column.loop,
      valueChangeBehavior: type === 'period' ? 'smooth' : 'auto',
      renderLabel: (option) => <div className={classNames.item}>{option.label}</div>,
    };
  });

  return (
    <WheelPicker
      ref={ref}
      columns={columns}
      itemSize={ITEM_SIZE}
      visibleItemCount={VISIBLE_ITEM_COUNT}
      disabled={disabled}
      aria-label={ariaLabelledby ? ariaLabel : (ariaLabel ?? defaultAriaLabels.root)}
      aria-labelledby={ariaLabelledby}
      className={clsx(classNames.root, className)}
      {...props}
    />
  );
});

/**
 * This file is a snippet from SEED Design, helping you get started quickly with @seed-design/* packages.
 * You can extend this snippet however you want.
 */
