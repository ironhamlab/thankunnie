import { create } from 'zustand';
import { BOOKINGS, MENUS, SERVICES, SHOP, SLOTS, overlaps } from './data';

export const useDesignStore = create((set, get) => ({
  shop: SHOP,
  menus: MENUS,
  bookings: BOOKINGS,
  slots: SLOTS,
  hold: null,
  search: '',
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
  saveSlot: (slot) => {
    const menu = get().menus.find((item) => item.id === slot.serviceId);
    if (!menu?.active) throw new Error('활성 시술을 선택해 주세요.');
    if (
      get().slots.some(
        (item) =>
          item.id !== slot.id &&
          item.date === slot.date &&
          item.status !== 'stopped' &&
          overlaps(
            slot.time,
            menu.duration,
            item.time,
            get().menus.find((m) => m.id === item.serviceId)?.duration ?? 60,
          ),
      )
    )
      throw new Error('기존 빈자리와 시간이 겹칩니다. 다른 시간을 선택해 주세요.');
    set((state) => ({
      slots: state.slots.some((item) => item.id === slot.id)
        ? state.slots.map((item) => (item.id === slot.id ? slot : item))
        : [...state.slots, slot],
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
      name: '김언니',
      phone: '010-12**-56**',
      request,
      status: 'confirmed',
      isCustomer: true,
    };
    set((state) => ({ bookings: [booking, ...state.bookings], hold: null }));
    return booking.id;
  },
  cancelBooking: (id) =>
    set((state) => ({
      bookings: state.bookings.map((item) =>
        item.id === id && item.status === 'confirmed'
          ? { ...item, status: 'cancelled', refund: Math.round(item.deposit * 0.5) }
          : item,
      ),
    })),
}));
