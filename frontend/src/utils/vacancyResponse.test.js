import assert from 'node:assert/strict';
import { test } from 'node:test';
import { toVacancyService, hasValidCoordinates } from './vacancyResponse.js';

const vacancy = {
  vacancyId: '3-202610281530',
  treatmentId: 3,
  shopName: '매장',
  treatmentName: '시술',
  startsAt: '2026-10-28T15:30:00+09:00',
  latitude: 0,
  longitude: 0,
  salePrice: 0,
  regularPrice: 30000,
  discountRate: 100,
  durationMinutes: 60,
  categoryName: '네일',
  regionCode: '11680',
  priceTier: 'LAST_MINUTE',
};

test('서버 가격·좌표와 기존 카드 필드를 보존하며 원본을 변경하지 않는다', () => {
  const input = Object.freeze({ ...vacancy });
  const service = toVacancyService(input, '2026-10-28');
  assert.equal(service.shop, '매장');
  assert.equal(service.name, '시술');
  assert.equal(service.price, 0);
  assert.equal(service.original, 30000);
  assert.equal(service.discount, 100);
  assert.equal(service.time, '15:30');
  assert.equal(service.day, 0);
  assert.equal(service.region, '강남/서초');
  assert.equal(service.id, vacancy.vacancyId);
  assert.equal(service.latitude, 0);
  assert.equal(service.longitude, 0);
  assert.equal(input.id, undefined);
});

test('KST 자정 경계와 같은 시술의 다른 시작 시각을 구분한다', () => {
  const service = toVacancyService(
    { ...vacancy, startsAt: '2026-10-28T15:00:00Z', vacancyId: '3-202610290000' },
    '2026-10-28',
  );
  assert.equal(service.day, 1);
  assert.equal(service.dateLabel, '2026-10-29');
  assert.equal(service.time, '00:00');
  assert.notEqual(service.id, toVacancyService(vacancy, '2026-10-28').id);
});

test('좌표 0을 허용하고 누락·문자열·범위 밖 좌표는 제외한다', () => {
  const service = toVacancyService(vacancy, '2026-10-28');
  assert.ok(hasValidCoordinates(service));
  for (const latitude of [null, undefined, '37', NaN, Infinity, 91]) {
    assert.ok(!hasValidCoordinates({ ...service, latitude }));
  }
  assert.ok(!hasValidCoordinates({ ...service, longitude: 181 }));
  assert.ok(hasValidCoordinates({ lat: 37, lng: 127 }));
});

test('잘못된 가격과 시각을 임의 기본값으로 대체하지 않는다', () => {
  for (const salePrice of [null, undefined, '0', -1]) {
    assert.throws(() => toVacancyService({ ...vacancy, salePrice }));
  }
  assert.throws(() => toVacancyService({ ...vacancy, startsAt: 'invalid' }));
});
