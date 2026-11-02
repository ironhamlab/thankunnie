import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(directory, '../../..');
const plan = JSON.parse(fs.readFileSync(path.join(directory, 'data-plan.json'), 'utf8'));
const quote = (value) => value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`;
const timestamp = (value) => `TIMESTAMP ${quote(value)}`;
const created = timestamp('2026-11-02 09:00:00');
const begin = '-- BEGIN UNNIE-164 SEOUL DEMO ADDITIONS';
const end = '-- END UNNIE-164 SEOUL DEMO ADDITIONS';
const sql = [begin, `-- 서울 신규 가상 매장 50 / 일반 시술 130 / 초보 시술 20 (16개 매장).
-- 기존 02 및 03의 시연·결제·예약 데이터는 유지한다. 01 스키마와 CATEGORY 1~5가 선행 조건이다.
-- ID는 SEQ_SHOP / SEQ_TREATMENT / SEQ_SHOP_BUSINESS_HOUR / SEQ_SHOP_SCHEDULE_EXCEPTION.NEXTVAL로 발급한다.
-- 앱과 같은 시퀀스를 소비하므로 고정 PK나 시퀀스 재시작 없이 이후 앱 생성과 충돌하지 않는다.
-- 신규 로그인 ID는 demo164_s01~demo164_s50이다. PASSWORD_HASH는 명백한 더미 CHANGE_ME이며 로그인할 수 없다.
-- 실제 자격 증명은 작성하지 않는다. 사장님 로그인·결제 시연은 기존 초기/시연 계정을 사용한다.
-- 주소·좌표는 OpenStreetMap 원본의 공개 주소 지점으로 대조했다. 실제 입점·업체 정보가 아니다.
-- OSM 기여자, ODbL 1.0: https://www.openstreetmap.org/copyright
-- 원본 지점·조회일·출처: docs/demo/164/location-sources/*.json (2026-11-02 확인).
-- 좌표는 지도 시연용 주소 지점이며 측량·출입구 위치 정확도를 보증하지 않는다.
-- 사진은 별도 생성·업로드 후 연결한다. 업로드 전 IMAGE_URL은 NULL이며 없는 경로를 넣지 않는다.
-- local H2는 앱 시작 시 전체 03을 자동 로드한다. Oracle은 수동 실행한다.
-- 신규 블록만 재실행하면 로그인 ID/매장별 시술명/요일/OPEN 시작·종료로 중복을 건너뛴다.
-- 기존 03 본문은 고정 PK를 사용하므로 이미 적용한 DB에 전체 03을 재실행하지 않는다.
-- 기존 DB에는 대상 환경 확인 후 docs/demo/164/demo164-additions.sql만 실행한다.
-- 전용 시연 DB에서 트랜잭션으로 실행하고 집계/FK/필수값 확인 후 사람이 COMMIT한다.
-- 이 파일은 DB에 자동 COMMIT하거나 기존 데이터를 삭제하지 않는다.
`];
const counts = { shops: 0, normal: 0, model: 0, hours: 0, openings: 0 };
const weekdays = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const prices = [
  [45000, 40000, 65000, 60000, 45000, 25000, 65000, 75000, 45000, 40000],
  [45000, 40000, 130000, 140000, 100000, 90000, 35000, 70000, 110000, 65000, 35000, 45000],
  [45000, 50000, 75000, 80000, 55000, 85000],
  [65000, 70000, 60000, 75000, 80000, 70000],
  [70000, 90000, 60000, 100000, 75000, 110000],
];
const durations = [[60,60,90,60,60,30,90,90,60,60], [60,60,120,150,120,120,30,60,120,90,30,60], [60,60,90,90,90,90], [60,60,60,90,90,60], [60,90,60,90,60,90]];
const details = [
  '손톱 길이와 모양을 상담하고 큐티클 정리부터 마무리 보습까지 진행합니다. 기존 젤 제거는 예약 전 상담해 주세요.',
  '모발 상태와 평소 손질 방법을 살펴 디자인을 상담합니다. 샴푸와 마무리 스타일링을 포함하며 추가 작업은 먼저 안내합니다.',
  '눈매와 기존 속눈썹 상태를 확인하고 자연스러운 컬을 상담합니다. 시술 전에는 눈가 화장을 지워 주세요.',
  '피부 상태를 상담한 뒤 클렌징과 해당 관리, 보습 마무리를 진행합니다. 자극이 느껴지면 즉시 알려 주세요.',
  '원하는 분위기와 일정에 맞춰 색감을 상담합니다. 베이스부터 포인트 표현까지 진행하며 헤어 스타일링은 포함하지 않습니다.',
];
const findShop = (shop) => `(SELECT SHOP_ID FROM SHOP WHERE LOGIN_ID = ${quote(shop.loginId)})`;
for (const shop of plan.shops) {
  const c = shop.categoryId - 1;
  const source = JSON.parse(fs.readFileSync(path.join(directory, 'location-sources', `${shop.regionCode}.json`), 'utf8'));
  const koreanAddresses = source.locations.filter((location) => /[가-힣]/.test(location.street) && /^\d+[\d-]*$/.test(location.houseNumber));
  const unique = [...new Map(koreanAddresses.map((location) => [`${location.street} ${location.houseNumber}`, location])).values()];
  const location = unique[shop.districtIndex - 1];
  if (!location || !/[가-힣]/.test(location.street) || !/^\d+[\d-]*$/.test(location.houseNumber)) throw new Error(`Unverified Korean address: ${shop.loginId}`);
  if (!(location.latitude > 37.4 && location.latitude < 37.75 && location.longitude > 126.7 && location.longitude < 127.25)) throw new Error(`Invalid Seoul coordinates: ${shop.loginId}`);
  const address = `서울특별시 ${shop.district} ${location.street} ${location.houseNumber}`;
  sql.push(`-- ${shop.number}. ${shop.name}: 일반 ${shop.normalCount}, 모델 ${shop.modelCount}; OSM ${location.osmType}/${location.osmId}`);
  sql.push(`INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS, ADDRESS_DETAIL,
    LATITUDE, LONGITUDE, PHONE, DESCRIPTION, NOTICE, STATUS, CREATED_AT, UPDATED_AT)
SELECT SEQ_SHOP.NEXTVAL, ${shop.categoryId}, ${quote(shop.loginId)}, 'CHANGE_ME', ${quote(shop.name)}, ${quote(shop.regionCode)}, ${quote(address)}, '시연용 가상공간',
    ${location.latitude.toFixed(7)}, ${location.longitude.toFixed(7)}, '02-0000-0000', ${quote(`${shop.district}에서 편안하게 머무는 ${shop.name}입니다. 취향과 컨디션을 충분히 상담하고 한 분씩 정성껏 관리합니다. 시연을 위해 만든 가상 매장입니다.`)},
    '시연용 가상 매장입니다. 주소는 지도 시연용이며 실제 입점 정보가 아닙니다. 표시된 전화번호는 연결되지 않는 더미입니다.', 'ACTIVE', ${created}, ${created}
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM SHOP WHERE LOGIN_ID = ${quote(shop.loginId)});`);
  counts.shops++;
  for (const treatment of shop.treatments) {
    const model = treatment.type === 'MODEL';
    const regular = prices[c][treatment.menuIndex] + ((shop.number % 3) * 2000);
    const price = Math.floor(regular * (model ? 0.4 : [1, 0.9, 0.85][shop.number % 3]) / 100) * 100;
    const last = Math.floor(price * (shop.number % 4 === 0 ? 1 : 0.85) / 100) * 100;
    const duration = durations[c][treatment.menuIndex] + (model ? 30 : 0);
    const condition = model ? '성인 참여자를 모집합니다. 지도 담당자와 함께하는 초보 시술로 시간이 더 걸릴 수 있습니다. 가능한 디자인과 시술 이력을 사전 상담해 주세요.' : null;
    const photo = model ? '상담 후 동의한 범위에서 시술 부위의 전후 사진을 촬영합니다. 얼굴 전체와 성명은 촬영·공개하지 않습니다. 사용 목적과 공개 범위를 먼저 안내합니다.' : null;
    sql.push(`INSERT INTO TREATMENT (TREATMENT_ID, SHOP_ID, NAME, DESCRIPTION, REGULAR_PRICE, PRICE, LAST_MINUTE_PRICE,
    DURATION_MINUTES, TREATMENT_TYPE, MODEL_CONDITION, PREPARATION_NOTES, PHOTO_NOTICE, STATUS, CREATED_AT, UPDATED_AT)
SELECT SEQ_TREATMENT.NEXTVAL, ${findShop(shop)}, ${quote(treatment.name)}, ${quote((model ? '초보 시술 모델 모집입니다. ' : '') + details[c])}, ${regular}, ${price}, ${last},
    ${duration}, ${quote(treatment.type)}, ${quote(condition)}, '예약 시간 10분 전 도착해 주세요. 기존 시술 이력과 알레르기 등 상담할 내용을 미리 알려 주세요.', ${quote(photo)}, 'ACTIVE', ${created}, ${created}
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TREATMENT WHERE SHOP_ID = ${findShop(shop)} AND NAME = ${quote(treatment.name)});`);
    counts[model ? 'model' : 'normal']++;
  }
  const closed = shop.number % 7;
  const open = [9,10,11][shop.number % 3];
  const close = [19,20,21][shop.number % 3];
  for (let day = 0; day < weekdays.length; day++) {
    const isClosed = day === closed;
    sql.push(`INSERT INTO SHOP_BUSINESS_HOUR (BUSINESS_HOUR_ID, SHOP_ID, DAY_OF_WEEK, OPEN_TIME, CLOSE_TIME, IS_CLOSED)
SELECT SEQ_SHOP_BUSINESS_HOUR.NEXTVAL, ${findShop(shop)}, ${quote(weekdays[day])}, ${quote(isClosed ? null : `${String(open).padStart(2,'0')}:00`)}, ${quote(isClosed ? null : `${close}:00`)}, ${quote(isClosed ? 'Y' : 'N')}
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM SHOP_BUSINESS_HOUR WHERE SHOP_ID = ${findShop(shop)} AND DAY_OF_WEEK = ${quote(weekdays[day])});`);
    counts.hours++;
  }
  for (let day = 2; day <= 20; day++) {
    const date = new Date(Date.UTC(2026,10,day));
    if ((date.getUTCDay() + 6) % 7 === closed) continue;
    const dateText = `2026-11-${String(day).padStart(2,'0')}`;
    const starts = timestamp(`${dateText} ${open + 1}:00:00`);
    const ends = timestamp(`${dateText} ${close - 1}:00:00`);
    sql.push(`INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE, STARTS_AT, ENDS_AT, REASON, CREATED_AT, UPDATED_AT)
SELECT SEQ_SHOP_SCHEDULE_EXCEPTION.NEXTVAL, ${findShop(shop)}, NULL, 'OPEN', ${starts}, ${ends}, 'UNNIE-164 가상 매장 시연 모집', ${created}, ${created}
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM SHOP_SCHEDULE_EXCEPTION WHERE SHOP_ID = ${findShop(shop)} AND EXCEPTION_TYPE = 'OPEN' AND STARTS_AT = ${starts} AND ENDS_AT = ${ends});`);
    counts.openings++;
  }
  sql.push('');
}
if (counts.shops !== 50 || counts.normal !== 130 || counts.model !== 20 || counts.hours !== 350) throw new Error('Data count mismatch');
sql.push(`-- 생성 집계: ${JSON.stringify(counts)}`, end, '');
const segment = sql.join('\n');
const target = path.join(root, 'docs/erd/03_demo_data_dml.sql');
const current = fs.readFileSync(target, 'utf8');
const start = current.indexOf(begin);
const finish = current.indexOf(end);
if ((start < 0) !== (finish < 0)) throw new Error('Incomplete UNNIE-164 marker; inspect manually');
const output = start < 0 ? `${current.trimEnd()}\n\n${segment}` : current.slice(0,start) + segment + current.slice(finish + end.length).replace(/^\r?\n/, '');
fs.writeFileSync(target, output, 'utf8');
fs.writeFileSync(path.join(directory, 'demo164-additions.sql'), segment, 'utf8');
console.info(JSON.stringify(counts));
