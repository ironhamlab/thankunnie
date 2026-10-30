/**
 * 약관 코드와 화면 문구. 코드·필수 여부의 기준은 서버(GET /api/v1/terms, docs/api/terms.md)이고,
 * 본문은 프론트에서 관리한다.
 */
export const TERMS_CODE = {
  AGE_14: 'AGE_14',
  SERVICE: 'SERVICE',
  PRIVACY: 'PRIVACY',
  THIRD_PARTY: 'THIRD_PARTY',
  SMS_NOTIFICATION: 'SMS_NOTIFICATION',
  MODEL_PHOTO: 'MODEL_PHOTO',
};

// 회원가입 화면에 보이지 않는 약관. 모델 촬영 동의는 첫 모델 시술 예약 때 따로 받는다
export const SIGNUP_HIDDEN_TERMS = [TERMS_CODE.MODEL_PHOTO];

// 동의가 없어 막혔을 때 서버가 돌려주는 에러 코드 → 받아야 할 약관
export const AGREEMENT_REQUIRED_ERRORS = {
  TERMS_003: TERMS_CODE.SMS_NOTIFICATION,
  TERMS_004: TERMS_CODE.MODEL_PHOTO,
};

export const TERMS_CONTENT = {
  [TERMS_CODE.AGE_14]: {
    summary: '만 14세 이상입니다.',
    body: '땡잡은 언니는 만 14세 이상만 가입할 수 있습니다.',
  },
  [TERMS_CODE.SERVICE]: {
    summary: '서비스 이용약관',
    body:
      '여러 뷰티샵의 예약 가능 시간을 조회하고 예약금을 결제해 예약하는 서비스입니다. ' +
      '예약 확정은 예약금 결제가 끝난 뒤이며, 취소·환불은 취소 정책(당일 0%, 하루 전 50%, 이틀 전까지 100%)을 따릅니다.',
  },
  [TERMS_CODE.PRIVACY]: {
    summary: '개인정보 수집·이용',
    body:
      '수집 항목: 아이디, 이름, 휴대폰 번호. 이용 목적: 회원 식별, 예약 확인, 매장 연락. ' +
      '보유 기간: 회원 탈퇴 시까지.',
  },
  [TERMS_CODE.THIRD_PARTY]: {
    summary: '개인정보 제3자 제공',
    body:
      '제공받는 자: 예약한 매장. 제공 항목: 이름, 휴대폰 번호. ' +
      '목적: 예약 확인과 시술 관련 연락. 보유 기간: 시술 완료 후 매장 정책에 따름.',
  },
  [TERMS_CODE.SMS_NOTIFICATION]: {
    summary: '관심 시간 알림 문자 수신',
    body:
      '찜한 매장이나 관심 조건에 맞는 빈자리가 생기면 등록한 휴대폰 번호로 문자를 보냅니다. ' +
      '알림 조건을 해제하면 더 이상 받지 않습니다.',
  },
  [TERMS_CODE.MODEL_PHOTO]: {
    summary: '모델 시술 촬영·결과물 활용',
    body:
      '모델 시술은 매장의 포트폴리오를 위해 시술 과정과 결과를 촬영할 수 있습니다. ' +
      '촬영물은 매장의 포트폴리오·SNS 게시에 활용될 수 있으며, 자세한 내용은 시술별 촬영 안내를 확인해 주세요.',
  },
};

// 동의 모달 제목·버튼 문구
export const AGREEMENT_DIALOG = {
  [TERMS_CODE.SMS_NOTIFICATION]: {
    title: '알림 문자 수신 동의',
    description: '알림을 받으려면 문자 수신에 동의해 주세요.',
  },
  [TERMS_CODE.MODEL_PHOTO]: {
    title: '모델 시술 촬영 동의',
    description: '모델 시술을 예약하려면 촬영과 결과물 활용에 동의해 주세요. 한 번 동의하면 다음부터 묻지 않아요.',
  },
};
