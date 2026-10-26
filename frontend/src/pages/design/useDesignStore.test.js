import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { useDesignStore } from './useDesignStore.js';
import { DEMO_DATE } from './data.js';

const initial = useDesignStore.getState();
afterEach(() => useDesignStore.setState(initial, true));

const menu = { id: 'treatment-1000', name: '서버 시술', duration: 60, original: 50000, active: true };
const slot = { id: 'new', serviceId: menu.id, date: DEMO_DATE, time: '10:00', discount: 25, status: 'selling' };

test('서버 활성 목록에 없는 시술은 저장을 거절하고 기존 빈자리·예약을 보존한다', () => {
  assert.throws(() => initial.saveSlot(slot, []), /활성 시술/);
  assert.throws(() => initial.saveSlot(slot, [{ ...menu, active: false }]), /활성 시술/);
  assert.equal(useDesignStore.getState().slots, initial.slots);
  assert.equal(useDesignStore.getState().bookings, initial.bookings);
});

test('활성 시술로 등록한 빈자리는 이후 비활성화되어도 이력과 메뉴 스냅샷을 보존한다', () => {
  useDesignStore.setState({ slots: [], bookings: [] });
  initial.saveSlot(slot, [menu]);
  const saved = useDesignStore.getState();
  assert.equal(saved.slots.length, 1);
  assert.deepEqual(saved.menus.find((item) => item.id === menu.id), menu);
  assert.throws(() => saved.saveSlot({ ...slot, id: 'next', time: '11:00' }, []), /활성 시술/);
  assert.equal(useDesignStore.getState().slots, saved.slots);
  assert.equal(useDesignStore.getState().menus, saved.menus);
});
