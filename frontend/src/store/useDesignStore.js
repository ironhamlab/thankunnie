import { create } from 'zustand';
import {
  BOOKINGS,
  DEMO_DATE,
  DEMO_NOW,
  MENUS,
  SERVICES,
  SHOP,
  SLOTS,
  getPayment,
  overlaps,
  resolveSlotMenu,
  validateSlot,
} from '../utils/demoData.js';
import { quoteRefund } from '../utils/demoRefundPolicy.js';

// 모의 PG 환불 처리 시간. 환불 처리 중(REFUND_PENDING) 상태를 화면에서 확인할 수 있게 둔다.
const REFUND_DEMO_DELAY_MS = 1500;
const DEMO_REFUNDED_AT = `${DEMO_DATE}T14:51:00+09:00`;

export const useDesignStore = create((set, get) => ({
  shop: SHOP,
  menus: MENUS,
  bookings: BOOKINGS,
  slots: SLOTS.map((slot) => ({
    ...slot,
    duration: MENUS.find((menu) => menu.id === slot.serviceId).duration,
  })),
  hold: null,
  search: '',
  profile: { name: '김언니', phone: '010-12**-56**', alerts: true },
  favorites: ['nail', 'hair', 'lash'],
  lastBookingId: null,
  saveProfile: (profile) => set((state) => ({ profile: { ...state.profile, ...profile } })),
  toggleFavorite: (id) =>
    set((state) => ({
      favorites: state.favorites.includes(id)
        ? state.favorites.filter((item) => item !== id)
        : [...state.favorites, id],
    })),
  setSearch: (search) => set({ search }),
  saveShop: (shop) => set({ shop }),
  saveMenu: (menu) =>
    set((state) => ({
      menus: state.menus.some((item) => item.id === menu.id)
        ? state.menus.map((item) => (item.id === menu.id ? menu : item))
        : [...state.menus, menu],
    })),
  toggleMenu: (id) =>
    set((state) => ({
      menus: state.menus.map((item) => (item.id === id ? { ...item, active: !item.active } : item)),
    })),
  saveSlot: (slot, selectableMenus = []) => {
    const existingSlot = get().slots.find((item) => item.id === slot.id);
    const existingMenu = resolveSlotMenu(existingSlot, selectableMenus, get().menus);
    const keepTreatment = existingSlot && (slot.serviceId === existingSlot.serviceId
      || slot.serviceId === existingMenu?.id);
    const selectedMenu = keepTreatment
      ? { ...get().menus.find((menu) => menu.id === existingSlot.serviceId),
        id: existingSlot.serviceId, duration: existingSlot.duration, active: true }
      : selectableMenus.find((menu) => menu.id === slot.serviceId && menu.active);
    if (!selectedMenu) throw new Error('활성 시술을 선택해 주세요.');
    // 동일 시술의 수정은 기존 스냅샷을 보존한다. 신규·시술 교체에만 최신 활성 메뉴를 적용한다.
    const savedSlot = {
      ...slot,
      serviceId: selectedMenu.id,
      treatmentId: keepTreatment ? existingSlot.treatmentId : selectedMenu.treatmentId,
      duration: selectedMenu.duration,
    };
    const validationError = validateSlot(savedSlot, [selectedMenu], get().slots, get().bookings);
    if (validationError) throw new Error(validationError);
    set((state) => ({
      menus: state.menus.some((menu) => menu.id === selectedMenu.id)
        ? state.menus
        : [...state.menus, selectedMenu],
      slots: state.slots.some((item) => item.id === slot.id)
        ? state.slots.map((item) => (item.id === slot.id ? savedSlot : item))
        : [...state.slots, savedSlot],
    }));
  },
  toggleSlot: (id) =>
    set((state) => ({
      slots: state.slots.map((item) =>
        item.id === id && ['selling', 'stopped'].includes(item.status)
          ? { ...item, status: item.status === 'selling' ? 'stopped' : 'selling' }
          : item,
      ),
    })),
  createHold: (serviceId, date, time, general = false) => {
    const service = SERVICES.find((item) => item.id === serviceId);
    if (!service) throw new Error('시술 정보를 찾을 수 없습니다.');
    const duration = general ? 90 : service.duration;
    if (
      get().bookings.some(
        (item) =>
          item.status === 'confirmed' &&
          item.serviceId === serviceId &&
          item.date === date &&
          overlaps(time, duration, item.time, item.duration),
      )
    )
      throw new Error('이미 예약된 시간입니다. 다른 시간을 선택해 주세요.');
    const price = general ? service.original : service.price;
    set({
      hold: {
        serviceId,
        date,
        time,
        duration,
        price,
        deposit: Math.round(price * 0.2),
        expiresAt: Date.now() + 300000,
        general,
      },
    });
  },
  releaseHold: () => set({ hold: null }),
  expireHold: () => set((state) => ({ hold: state.hold ? { ...state.hold, expiresAt: 0 } : null })),
  confirmBooking: (request) => {
    const hold = get().hold;
    if (!hold || hold.expiresAt <= Date.now())
      throw new Error('임시 확보 시간이 만료되었습니다. 시간을 다시 선택해 주세요.');
    const booking = {
      ...hold,
      id: `BK-DEMO-${Date.now()}`,
      name: get().profile.name,
      phone: get().profile.phone,
      request,
      status: 'confirmed',
      isCustomer: true,
    };
    set((state) => ({
      bookings: [booking, ...state.bookings],
      hold: null,
      lastBookingId: booking.id,
    }));
    return booking.id;
  },
  // 환불 정책으로 환불액을 계산해 취소를 접수하고, 환불할 금액이 있으면 모의 PG 환불을 진행한다.
  cancelBooking: (id, reason = '개인 일정 변경') => {
    const booking = get().bookings.find((item) => item.id === id && item.status === 'confirmed');
    if (!booking) return;
    const quote = quoteRefund(booking);
    const payment = getPayment(booking);
    const hasRefund = quote.refundAmount > 0;
    set((state) => ({
      bookings: state.bookings.map((item) =>
        item.id === id
          ? {
              ...item,
              status: 'cancelled',
              refund: quote.refundAmount,
              cancellation: {
                reason,
                requestedAt: DEMO_NOW,
                tierKey: quote.tier.key,
                policyRefundAmount: quote.refundAmount,
              },
              payment: { ...payment, status: hasRefund ? 'REFUND_PENDING' : payment.status },
            }
          : item,
      ),
    }));
    if (hasRefund) setTimeout(() => get().completeRefund(id), REFUND_DEMO_DELAY_MS);
  },
  completeRefund: (id) =>
    set((state) => ({
      bookings: state.bookings.map((item) => {
        if (item.id !== id || item.payment?.status !== 'REFUND_PENDING') return item;
        const refundedAmount = item.payment.refundedAmount + item.refund;
        return {
          ...item,
          payment: {
            ...item.payment,
            refundedAmount,
            refundedAt: DEMO_REFUNDED_AT,
            status: refundedAmount >= item.payment.amount ? 'REFUNDED' : 'PART_REFUNDED',
          },
        };
      }),
    })),
}));
