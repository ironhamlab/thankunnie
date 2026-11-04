/**
 * @file ui:wheel-picker
 * @requires @seed-design/react@^2.4.0 || ^3.0.0
 **/

import { WheelPicker as SeedWheelPicker } from '@seed-design/react';
import * as React from 'react';

function validateOptionLabels(columns) {
  if (import.meta.env.PROD) return;

  for (const column of columns) {
    for (const option of column.options) {
      if (React.isValidElement(option.label) && !option.ariaLabel) {
        console.warn(
          `WheelPicker: "${column.id}" 컬럼에서 React 요소 label을 사용하는 option에는 ariaLabel이 필요합니다.`,
        );
      }
    }
  }
}

/**
 * 여러 Wheel Picker 컬럼을 데이터 배열로 구성합니다.
 *
 * @see https://seed-design.io/react/components/wheel-picker
 */
export const WheelPicker = React.forwardRef(function WheelPicker(
  { columns, ...rootProps },
  ref,
) {
  validateOptionLabels(columns);

  return (
    <SeedWheelPicker.Root ref={ref} {...rootProps}>
      {columns.map(({ id, options, ...columnProps }) => (
        <SeedWheelPicker.Column key={id} options={options} {...columnProps} />
      ))}
    </SeedWheelPicker.Root>
  );
});

/**
 * This file is a snippet from SEED Design, helping you get started quickly with @seed-design/* packages.
 * You can extend this snippet however you want.
 */
