import test from 'node:test';
import { URL } from 'node:url';
import assert from 'node:assert/strict';
import { buildMainParams } from '../src/constants/mainPresets.js';
import {
  buildSelectedSearchParams,
  getExploreHref,
  getItemDetailHref,
} from '../src/utils/mainNavigation.js';
import {
  formatItemDate,
  getItemImageUrl,
  formatItemPrice,
  getItemStatusLabel,
  hasDiscount,
} from '../src/utils/mainItemDisplay.js';

test('더보기는 프리셋 필터를 보존하고 첫 페이지를 전달한다', () => {
  for (const preset of ['TODAY', 'CLOSING_SOON']) {
    const params = buildMainParams(preset, new Date('2026-10-23T03:00:00Z'));
    const url = new URL(
      getExploreHref({ ...params, page: 5, internal: 'hidden' }),
      'https://example.test',
    );
    assert.equal(url.pathname, '/explore');
    for (const [key, value] of Object.entries(params)) {
      assert.equal(url.searchParams.get(key), String(value));
    }
    assert.equal(url.searchParams.has('internal'), false);
  }
});

test('선택 날짜와 정렬을 탐색 조건으로 전달하며 선택 없는 날짜는 생략한다', () => {
  const params = buildSelectedSearchParams({ date: '2026-10-25', sort: 'CLOSING_ASC' });
  const query = new URL(getExploreHref(params), 'https://example.test').searchParams;
  assert.equal(query.get('startDate'), '2026-10-25');
  assert.equal(query.get('endDate'), '2026-10-25');
  assert.equal(query.get('sort'), 'CLOSING_ASC');
  assert.equal(query.get('availableOnly'), 'true');
  assert.equal(query.get('page'), '0');
  assert.equal(query.has('closingWithinHours'), false);
  assert.equal('startDate' in buildSelectedSearchParams({ date: '' }), false);
  assert.throws(() => buildSelectedSearchParams({ date: '2026-02-30' }), RangeError);
  assert.throws(() => buildSelectedSearchParams({ sort: 'UNKNOWN' }), RangeError);
});

test('상세 경로에는 유효한 ID만 전달한다', () => {
  assert.equal(getItemDetailHref(101), '/items/101');
  for (const id of [null, undefined, 0, -1, '../admin', 1.5, Infinity]) {
    assert.equal(getItemDetailHref(id), null);
  }
});

test('0원은 유효한 가격이며 누락 가격과 알 수 없는 상태를 임의 보정하지 않는다', () => {
  assert.match(formatItemPrice(0), /0/);
  for (const price of [null, undefined, '', '12000', NaN, Infinity, -1]) {
    assert.equal(formatItemPrice(price), '가격 확인 필요');
  }
  assert.match(formatItemPrice(35000), /35,000/);
  assert.equal(getItemStatusLabel('AVAILABLE'), '예약 가능');
  assert.equal(getItemStatusLabel('HELD'), '임시 확보 중');
  assert.equal(getItemStatusLabel('BOOKED'), '예약 완료');
  assert.equal(getItemStatusLabel('SOLD_OUT'), '매진');
  assert.equal(getItemStatusLabel('CLOSED'), '마감');
  assert.equal(getItemStatusLabel('CANCELLED'), '취소');
  assert.equal(getItemStatusLabel('UNKNOWN'), '상태 확인 필요');
  assert.equal(getItemStatusLabel('toString'), '상태 확인 필요');
});

test('정상가가 판매가보다 높을 때만 할인 가격을 표시하고 잘못된 날짜를 방어한다', () => {
  assert.equal(hasDiscount({ price: 35000, originalPrice: 50000 }), true);
  assert.equal(hasDiscount({ price: 0, originalPrice: 50000 }), true);
  assert.equal(hasDiscount({ price: 50000, originalPrice: 35000 }), false);
  assert.equal(hasDiscount({ price: null, originalPrice: 35000 }), false);
  assert.equal(formatItemDate('invalid'), '일정 확인 필요');
  assert.equal(formatItemDate(null), '일정 확인 필요');
});

test('이미지는 API의 웹 URL만 사용하고 누락·잘못된 URL은 빈 이미지로 처리한다', () => {
  assert.equal(getItemImageUrl('/uploads/shop.jpg'), '/uploads/shop.jpg');
  assert.equal(getItemImageUrl('https://example.test/shop.jpg'), 'https://example.test/shop.jpg');
  for (const value of [
    null,
    undefined,
    '',
    123,
    'javascript:alert(1)',
    'data:image/svg+xml,test',
    '//example.test/shop.jpg',
    'not-a-url',
  ]) {
    assert.equal(getItemImageUrl(value), null);
  }
});
