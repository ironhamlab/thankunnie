// 화면 지역 묶음 → 시군구 코드(REGION_CODE, 5자리). 대응표는 docs/api/C-01-vacancy-search.md 9장
export const REGION_GROUPS = [
  {
    label: '강남/서초',
    regions: [
      { code: '11680', name: '강남구' },
      { code: '11650', name: '서초구' },
    ],
  },
  {
    label: '홍대/연남',
    regions: [
      { code: '11440', name: '마포구' },
      { code: '11410', name: '서대문구' },
    ],
  },
  { label: '마곡/강서', regions: [{ code: '11500', name: '강서구' }] },
  {
    label: '성수/건대',
    regions: [
      { code: '11200', name: '성동구' },
      { code: '11215', name: '광진구' },
    ],
  },
  { label: '잠실/송파', regions: [{ code: '11710', name: '송파구' }] },
  { label: '판교/분당', regions: [{ code: '41135', name: '성남시 분당구' }] },
];

const REGION_NAMES = Object.fromEntries(
  REGION_GROUPS.flatMap((group) => group.regions.map((region) => [region.code, region.name])),
);

/** 지역코드 → 이름. 목록에 없는 코드는 코드 그대로 보여준다 */
export const regionNameOf = (code) => REGION_NAMES[code] ?? code;
