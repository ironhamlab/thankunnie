/**
 * 로그인·가입 계정 종류. 고객·관리자는 회원 계정, 매장은 매장 계정으로 따로 가입·로그인한다.
 */
export const ACCOUNT_TYPE = {
  CUSTOMER: 'customer',
  SHOP: 'shop',
};

export const ACCOUNT_TYPE_OPTIONS = [
  { value: ACCOUNT_TYPE.CUSTOMER, label: '고객' },
  { value: ACCOUNT_TYPE.SHOP, label: '매장' },
];
