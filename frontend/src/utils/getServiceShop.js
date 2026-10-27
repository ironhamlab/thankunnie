export default function getServiceShop(service, ownerShop) {
  if (service?.id === 'nail') {
    return {
      name: ownerShop.name,
      address: [ownerShop.address, ownerShop.detail].filter(Boolean).join(' '),
    };
  }
  return {
    name: service?.shop || '매장 정보 없음',
    address: service?.location || '위치 정보 없음',
  };
}
