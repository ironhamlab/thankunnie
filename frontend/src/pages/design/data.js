// Figma의 예시 날짜를 고정하여 시연한다. 실제 API 데이터와 분리한다.
export const DEMO_DATE = '2025-10-24';
export const SERVICES = [
  {
    id: 'nail',
    name: '젤 네일 원컬러 + 케어',
    shop: '살롱 드 르네',
    location: '강남역 도보 3분',
    region: '강남/서초',
    category: '네일',
    duration: 60,
    day: 0,
    time: '15:30',
    original: 50000,
    price: 32500,
    discount: 35,
    urgency: '초임박 특가',
    image: '8-3-0.png',
    active: true,
    description: '기본 굳은살 및 큐티클 오프 케어 + 프리미엄 젤 1컬러',
  },
  {
    id: 'hair',
    name: '시그니처 컷 & 두피 스케일링',
    shop: '헤어룸 바이 민',
    location: '홍대입구역 9번출구',
    region: '홍대/연남',
    category: '헤어',
    duration: 50,
    day: 0,
    time: '17:00',
    original: 65000,
    price: 48000,
    discount: 26,
    urgency: '임박 특가',
    image: '8-3-1.png',
    active: true,
  },
  {
    id: 'lash',
    name: '속눈썹 펌 & 블랙틴팅',
    shop: '아이래쉬 오브',
    location: '신사 가로수길',
    region: '강남/서초',
    category: '속눈썹',
    duration: 45,
    day: 1,
    time: '11:00',
    original: 45000,
    price: 45000,
    discount: 0,
    urgency: '일반 빈자리',
    image: '8-3-2.png',
    active: false,
    description: '영양 펌 및 속눈썹 코팅',
  },
  {
    id: 'facial',
    name: '수분 진정 퀵 페이셜',
    shop: '에스테틱 라온',
    location: '성수 연무장길',
    region: '성수/건대',
    category: '피부관리',
    duration: 60,
    day: 0,
    time: '16:00',
    original: 90000,
    price: 54000,
    discount: 40,
    urgency: '초임박 특가',
    image: '8-3-3.png',
    active: true,
  },
  {
    id: 'art',
    name: '이달의 아트 (Design of Month)',
    shop: '글로우 네일',
    location: '판교역 1번출구',
    region: '판교/분당',
    category: '네일',
    duration: 80,
    day: 0,
    time: '18:30',
    original: 80000,
    price: 60000,
    discount: 25,
    urgency: '임박 특가',
    image: '8-3-4.png',
    active: true,
    description: '트렌디한 시즌 아트 디자인 10종 중 택1',
  },
  {
    id: 'clinic',
    name: '뿌리염색 & 클리닉',
    shop: '리프레쉬 헤어',
    location: '잠실새내역 도보 5분',
    region: '잠실/송파',
    category: '헤어',
    duration: 90,
    day: 2,
    time: '14:00',
    original: 75000,
    price: 75000,
    discount: 0,
    urgency: '일반 빈자리',
    image: '8-3-5.png',
    active: true,
  },
];
export const MENUS = [
  SERVICES[0],
  SERVICES[4],
  {
    id: 'pedi',
    name: '패디 기본 젤 + 풋 스파 케어',
    duration: 70,
    original: 65000,
    description: '발 각질 제거 및 젤 컬러링',
    active: true,
  },
  SERVICES[2],
];
export const SHOP = {
  name: '살롱 드 르네 강남점',
  category: '네일·패디',
  phone: '02-555-1234',
  address: '서울 서초구 강남대로 435',
  detail: '3층 (강남역 10번 출구 도보 3분)',
  description:
    '100% 예약제 프리미엄 젤 네일 전문 살롱입니다. 고객님의 손톱 건강과 위생을 최우선으로 생각하며 철저한 1인 1파일 원칙과 정밀 오토클레이브 멸균 시스템을 운영하고 있습니다. 감각적인 이달의 아트와 맞춤 시술을 경험해 보세요.',
};
export const BOOKINGS = [
  {
    id: 'BK-20251024-8842',
    serviceId: 'nail',
    name: '김언니',
    phone: '010-12**-56**',
    date: DEMO_DATE,
    time: '15:30',
    duration: 60,
    price: 32500,
    deposit: 6500,
    status: 'confirmed',
    request: '원장님 손톱 쉐입은 라운드로 부탁드려요.',
    isCustomer: true,
  },
  {
    id: 'BK-20251024-3109',
    serviceId: 'art',
    name: '박뷰티',
    phone: '010-98**-12**',
    date: DEMO_DATE,
    time: '11:00',
    duration: 80,
    price: 60000,
    deposit: 12000,
    status: 'completed',
    request: '',
  },
  {
    id: 'BK-20251023-7712',
    serviceId: 'nail',
    name: '최살롱',
    phone: '010-33**-44**',
    date: DEMO_DATE,
    time: '13:00',
    duration: 60,
    price: 32500,
    deposit: 6500,
    status: 'cancelled',
    refund: 3250,
    request: '',
  },
];
export const SLOTS = [
  {
    id: 'slot-1',
    serviceId: 'nail',
    date: DEMO_DATE,
    time: '15:30',
    status: 'reserved',
    discount: 35,
  },
  {
    id: 'slot-2',
    serviceId: 'art',
    date: DEMO_DATE,
    time: '17:00',
    status: 'selling',
    discount: 25,
  },
  { id: 'slot-3', serviceId: 'pedi', date: DEMO_DATE, time: '19:00', status: 'held', discount: 20 },
  {
    id: 'slot-4',
    serviceId: 'nail',
    date: '2025-10-25',
    time: '11:00',
    status: 'selling',
    discount: 0,
  },
  {
    id: 'slot-5',
    serviceId: 'art',
    date: '2025-10-25',
    time: '14:00',
    status: 'stopped',
    discount: 0,
  },
];
export const money = (amount) => `${amount.toLocaleString('ko-KR')}원`;
export const minutes = (time) => Number(time.split(':')[0]) * 60 + Number(time.split(':')[1]);
export const finish = (time, duration) =>
  `${String(Math.floor((minutes(time) + duration) / 60)).padStart(2, '0')}:${String((minutes(time) + duration) % 60).padStart(2, '0')}`;
export const overlaps = (time, duration, otherTime, otherDuration) =>
  minutes(time) < minutes(otherTime) + otherDuration &&
  minutes(otherTime) < minutes(time) + duration;
export const STATUS = {
  confirmed: '예약 확정',
  completed: '방문 완료',
  cancelled: '고객 취소',
  selling: '판매 중',
  held: '임시 확보',
  reserved: '예약 확정',
  stopped: '판매 중지',
};

export const serviceDate = (service) => {
  const date = new Date(DEMO_DATE + 'T00:00:00Z');
  date.setUTCDate(date.getUTCDate() + service.day);
  return date.toISOString().slice(0, 10);
};

// 명시적인 서버 ID가 있을 때만 연결한다. 데모 ID를 이름이나 순서로 추측하지 않는다.
export function resolveSlotMenu(slot, menus, historyMenus) {
  const historyMenu = historyMenus.find((menu) => menu.id === slot?.serviceId);
  const treatmentId = slot?.treatmentId ?? historyMenu?.treatmentId;
  return menus.find((menu) => menu.id === slot?.serviceId)
    ?? menus.find((menu) => treatmentId != null && menu.id === `treatment-${treatmentId}`);
}

export function validateSlot(slot, menus, slots, bookings) {
  const menu = menus.find((item) => item.id === slot.serviceId);
  if (!menu?.active) return '활성 시술을 선택해 주세요.';
  const duration = slot.duration ?? menu.duration;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(slot.date) || slot.date < DEMO_DATE || slot.date > '2025-11-24')
    return '시연 기준일부터 31일 이내 날짜를 선택해 주세요.';
  if (
    !/^\d{2}:(00|30)$/.test(slot.time) ||
    minutes(slot.time) < 600 ||
    minutes(slot.time) + duration > 1260
  )
    return '30분 단위로, 운영 시간(10:00~21:00) 안에 시술이 끝나도록 선택해 주세요.';
  if (
    !Number.isFinite(Number(slot.discount)) ||
    Number(slot.discount) < 0 ||
    Number(slot.discount) > 70
  )
    return '할인율은 0~70% 범위여야 합니다.';
  const occupied = slots.some(
    (item) =>
      item.id !== slot.id &&
      item.date === slot.date &&
      item.status !== 'stopped' &&
      overlaps(
        slot.time,
        duration,
        item.time,
        item.duration,
      ),
  );
  const booked = bookings.some(
    (item) =>
      item.status === 'confirmed' &&
      item.date === slot.date &&
      overlaps(slot.time, duration, item.time, item.duration),
  );
  if (occupied || booked)
    return '선택한 시간대에 기존 예약 또는 빈자리가 겹칩니다. 다른 시간을 선택해 주세요.';
  return '';
}
