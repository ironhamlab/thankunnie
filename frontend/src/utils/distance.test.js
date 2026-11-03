import assert from 'node:assert/strict';
import { test } from 'node:test';
import { distanceKm, formatDistance } from './distance.js';

const GANGNAM = { lat: 37.4979, lng: 127.0276 };
const SEOUL_STATION = { lat: 37.5547, lng: 126.9707 };

test('같은 위치는 0km 이다', () => {
  assert.equal(distanceKm(GANGNAM, GANGNAM), 0);
});

test('강남역에서 서울역까지 약 8km 이다', () => {
  const km = distanceKm(GANGNAM, SEOUL_STATION);
  assert.ok(km > 7.8 && km < 8.3, `${km}`);
  assert.equal(distanceKm(SEOUL_STATION, GANGNAM), km);
});

test('서버의 문자열 위경도도 숫자처럼 계산한다', () => {
  const fromString = distanceKm({ lat: '37.4979000', lng: '127.0276000' }, SEOUL_STATION);
  assert.equal(fromString, distanceKm(GANGNAM, SEOUL_STATION));
});

test('1km 안쪽은 10m 단위 m 로, 그 밖은 km 소수 1자리로 보여준다', () => {
  assert.equal(formatDistance(0), '10m');
  assert.equal(formatDistance(0.341), '350m');
  assert.equal(formatDistance(0.9999), '1.0km');
  assert.equal(formatDistance(1), '1.0km');
  assert.equal(formatDistance(12.345), '12.3km');
});
