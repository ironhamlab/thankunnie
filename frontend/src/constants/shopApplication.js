// 매장 가입 신청 상태별 탭 이름과 표시 색 (백엔드 ShopAccountStatus)
export const SHOP_APPLICATION_TABS = [
  { status: 'PENDING', label: '승인 대기', tone: undefined },
  { status: 'ACTIVE', label: '승인', tone: 'success' },
  { status: 'REJECTED', label: '거절', tone: 'neutral' },
];

export const SHOP_REVIEW_GUIDE = '네일, 헤어, 속눈썹, 피부 관리, 메이크업 매장만 승인해 주세요.';

// 거절 사유 빠른 선택. 매장에 그대로 보이는 문구다
export const SHOP_REJECT_REASONS = [
  '서비스 대상 업종이 아니에요.',
  '매장 정보가 부족해요.',
  '연락처를 확인할 수 없어요.',
];

export const SHOP_REJECT_REASON_MAX_LENGTH = 500;
