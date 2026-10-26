import { create } from 'zustand';
import { BOOKINGS, MENUS, SERVICES, SHOP, SLOTS, overlaps, validateSlot } from './data.js';

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
    const selectedMenu = selectableMenus.find((menu) => menu.id === slot.serviceId && menu.active);
    if (!selectedMenu) throw new Error('활성 시술을 선택해 주세요.');
    // 최신 시술 정보는 저장할 빈자리에만 적용하고 기존 빈자리의 소요 시간은 보존한다.
    const savedSlot = { ...slot, duration: selectedMenu.duration };
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
  cancelBooking: (id) =>
    set((state) => ({
      bookings: state.bookings.map((item) =>
        item.id === id && item.status === 'confirmed'
          ? { ...item, status: 'cancelled', refund: Math.round(item.deposit * 0.5) }
          : item,
      ),
    })),
}));
