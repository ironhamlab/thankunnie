// Figma의 예시 날짜를 고정하여 시연한다. 실제 API 데이터와 분리한다.
export const DEMO_DATE = '2025-10-24';
// 취소 시연 기준 시각. 원본 내 예약 화면의 '시술 40분 전' 구간(15:30 예약 기준)이다.
export const DEMO_NOW = `${DEMO_DATE}T14:50:00+09:00`;

// 예약금결제(DEPOSIT_PAYMENT) 응답 형태를 흉내 낸 모의 데이터를 만든다.
const depositPayment = ({
  id,
  amount,
  approvedAt,
  status = 'PAID',
  refundedAmount = 0,
  refundedAt = null,
}) => ({
  depositPaymentId: id,
  pgProvider: 'PORTONE',
  method: 'KG이니시스 · 신한카드 (일시불)',
  impUid: `imp_${String(id).padStart(12, '0')}`,
  status,
  amount,
  refundedAmount,
  approvedAt,
  refundedAt,
});
export const SERVICES = [
  {
    id: 'nail',
    name: '젤 네일 원컬러 + 케어',
    shop: '살롱 드 르네',
    location: '강남역 도보 3분',
    lat: 37.4982,
    lng: 127.0276,
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
    lat: 37.5571,
    lng: 126.9245,
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
    lat: 37.5206,
    lng: 127.0229,
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
    lat: 37.5431,
    lng: 127.056,
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
    lat: 37.3949,
    lng: 127.1112,
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
    lat: 37.5116,
    lng: 127.0863,
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
    payment: depositPayment({ id: 1042, amount: 6500, approvedAt: '2025-10-24T13:12:00+09:00' }),
  },
  {
    id: 'BK-20251025-5521',
    serviceId: 'hair',
    name: '김언니',
    phone: '010-12**-56**',
    date: '2025-10-25',
    time: '17:00',
    duration: 50,
    price: 48000,
    deposit: 20000,
    status: 'confirmed',
    request: '',
    isCustomer: true,
    payment: depositPayment({ id: 1038, amount: 20000, approvedAt: '2025-10-22T21:14:00+09:00' }),
  },
  {
    id: 'BK-20251028-1204',
    serviceId: 'clinic',
    name: '김언니',
    phone: '010-12**-56**',
    date: '2025-10-28',
    time: '14:00',
    duration: 90,
    price: 75000,
    deposit: 20000,
    status: 'confirmed',
    request: '뿌리 6cm 정도 자랐어요.',
    isCustomer: true,
    payment: depositPayment({ id: 1031, amount: 20000, approvedAt: '2025-10-21T10:05:00+09:00' }),
  },
  {
    id: 'BK-20251020-0931',
    serviceId: 'facial',
    name: '김언니',
    phone: '010-12**-56**',
    date: '2025-10-20',
    time: '16:00',
    duration: 60,
    price: 54000,
    deposit: 20000,
    status: 'cancelled',
    refund: 10000,
    request: '',
    isCustomer: true,
    payment: depositPayment({
      id: 1017,
      amount: 20000,
      approvedAt: '2025-10-16T19:40:00+09:00',
      status: 'PART_REFUNDED',
      refundedAmount: 10000,
      refundedAt: '2025-10-19T18:03:00+09:00',
    }),
    cancellation: {
      reason: '개인 일정 변경',
      requestedAt: '2025-10-19T18:02:00+09:00',
      tierKey: 'oneDay',
      policyRefundAmount: 10000,
    },
  },
  {
    id: 'BK-20251015-4410',
    serviceId: 'lash',
    name: '김언니',
    phone: '010-12**-56**',
    date: '2025-10-15',
    time: '11:00',
    duration: 45,
    price: 45000,
    deposit: 20000,
    status: 'cancelled',
    refund: 20000,
    request: '',
    isCustomer: true,
    payment: depositPayment({
      id: 1009,
      amount: 20000,
      approvedAt: '2025-10-08T12:30:00+09:00',
      status: 'REFUNDED',
      refundedAmount: 20000,
      refundedAt: '2025-10-11T09:21:00+09:00',
    }),
    cancellation: {
      reason: '다른 날짜로 다시 예약할게요',
      requestedAt: '2025-10-11T09:20:00+09:00',
      tierKey: 'twoDays',
      policyRefundAmount: 20000,
    },
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

// 예약금결제 상태. 백엔드 DepositPaymentStatus 값과 철자를 맞춘다.
export const PAYMENT_STATUS = {
  PAID: { label: '결제 완료', tone: 'paid' },
  REFUND_PENDING: { label: '환불 처리 중', tone: 'pending' },
  PART_REFUNDED: { label: '부분 환불', tone: 'partial' },
  REFUNDED: { label: '환불 완료', tone: 'refunded' },
};

// 결제 정보가 없는 예약(시연 중 새로 만든 예약 등)은 예약금 기준으로 결제 완료 상태를 만든다.
export const getPayment = (booking) =>
  booking.payment ??
  depositPayment({
    id: booking.id.replace(/\D/g, '').slice(-4) || 0,
    amount: booking.deposit,
    approvedAt: DEMO_NOW,
  });

// '2025-10-24T14:50:00+09:00' → '2025.10.24 14:50'. 시간대 변환 없이 표기만 바꾼다.
export const formatDateTime = (value) =>
  value ? `${value.slice(0, 10).replaceAll('-', '.')} ${value.slice(11, 16)}` : '-';

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
