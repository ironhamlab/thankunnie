export function hasValidCoordinates(item) {
  return (
    Number.isFinite(item.latitude) &&
    Math.abs(item.latitude) <= 90 &&
    Number.isFinite(item.longitude) &&
    Math.abs(item.longitude) <= 180
  );
}

export function validateVacancyPage(page) {
  if (
    !page ||
    !Array.isArray(page.content) ||
    !Number.isSafeInteger(page.totalElements) ||
    page.totalElements < 0 ||
    typeof page.hasNext !== 'boolean' ||
    !page.content.every(
      (item) =>
        item &&
        typeof item.vacancyId === 'string' &&
        item.vacancyId.length > 0 &&
        Number.isSafeInteger(item.treatmentId) &&
        item.treatmentId > 0 &&
        typeof item.shopName === 'string' &&
        item.shopName.trim() &&
        typeof item.treatmentName === 'string' &&
        item.treatmentName.trim() &&
        Number.isSafeInteger(item.salePrice) &&
        item.salePrice >= 0 &&
        Number.isFinite(Date.parse(item.startsAt)) &&
        Number.isFinite(Date.parse(item.bookingClosesAt)),
    )
  ) {
    throw new Error('빈자리 응답 형식이 올바르지 않습니다.');
  }
  return page;
}

export function vacancyMarkers(items) {
  return items.filter(hasValidCoordinates).map((item) => ({
    id: item.vacancyId,
    lat: item.latitude,
    lng: item.longitude,
    label: `${item.salePrice.toLocaleString('ko-KR')}원`,
    ariaLabel: `${item.shopName} ${item.salePrice.toLocaleString('ko-KR')}원`,
  }));
}
