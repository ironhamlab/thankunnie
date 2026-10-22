import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { useDesignStore } from './useDesignStore';
import { DEMO_DATE, STATUS, finish, minutes, money } from './data';
import Dialog from './Dialog';

export default function SlotsPage() {
  const slots = useDesignStore((s) => s.slots);
  const menus = useDesignStore((s) => s.menus);
  const save = useDesignStore((s) => s.saveSlot);
  const toggle = useDesignStore((s) => s.toggleSlot);
  const [params, setParams] = useSearchParams();
  const [edit, setEdit] = useState(null);
  const [status, setStatus] = useState('');
  const [serviceId, setServiceId] = useState('');
  const [date, setDate] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const blank = {
    id: 'slot-' + Date.now(),
    serviceId: menus.find((m) => m.active)?.id || '',
    date: DEMO_DATE,
    time: '17:00',
    discount: 25,
    status: 'selling',
  };
  const draft = edit || (params.has('new') ? blank : null);
  const close = () => {
    setEdit(null);
    setParams({});
    setError('');
  };
  const update = (key, value) => setEdit({ ...draft, [key]: value });
  const menu = menus.find((m) => m.id === draft?.serviceId);
  const submit = (e) => {
    e.preventDefault();
    setError('');
    if (minutes(draft.time) < 600 || minutes(draft.time) + (menu?.duration || 60) > 1260) {
      setError('운영 시간(10:00~21:00) 안에서 시술이 끝나도록 선택해 주세요.');
      return;
    }
    if (draft.date < DEMO_DATE) {
      setError('시연 기준일 이후 날짜를 선택해 주세요.');
      return;
    }
    try {
      save({ ...draft, discount: Number(draft.discount) });
      close();
      setNotice('빈자리를 저장했습니다.');
    } catch (err) {
      setError(err.message);
    }
  };
  const list = slots.filter(
    (s) =>
      (!status || s.status === status) &&
      (!serviceId || s.serviceId === serviceId) &&
      (!date || s.date === date),
  );
  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>
            빈자리 관리 <span className="b-badge">실시간 {slots.length}건</span>
          </h1>
          <p className="b-muted">당일 및 임박 타임세일 빈자리를 등록하고 판매 상태를 관리합니다.</p>
        </div>
        <Link className="b-button b-primary" to={R.SLOT_NEW}>
          + 신규 빈자리 등록
        </Link>
      </div>
      <div className="b-notice">
        <p>※ 확정된 예약은 빈자리 수정이나 판매 중지 동작으로 취소되지 않습니다.</p>
        <small>
          실제 결제와 자동 재공개는 백엔드 연결 후 제공됩니다. 현재 데모 상태만 변경됩니다.
        </small>
      </div>
      <div className="b-panel b-pad b-slot-filters">
        <label>
          일정 선택
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label>
          시술
          <select value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
            <option value="">전체 시술</option>
            {menus.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          판매 상태
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">전체</option>
            {['selling', 'held', 'reserved', 'stopped'].map((s) => (
              <option key={s} value={s}>
                {STATUS[s]}
              </option>
            ))}
          </select>
        </label>
        <button
          className="b-button"
          onClick={() => {
            setDate('');
            setServiceId('');
            setStatus('');
          }}
        >
          필터 초기화
        </button>
      </div>
      <section className="b-panel b-table-panel">
        <div className="b-pad">
          <h2>
            등록된 빈자리 목록 <small className="b-muted">(총 {list.length}개 타임슬롯)</small>
          </h2>
        </div>
        <div className="b-table-scroll">
          <table>
            <thead>
              <tr>
                {[
                  '시술명',
                  '등록 날짜',
                  '시작~종료 시각',
                  '정상가',
                  '할인',
                  '현재 적용 가격',
                  '판매 상태',
                  '관리 액션',
                ].map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {list.map((slot) => {
                const m = menus.find((item) => item.id === slot.serviceId);
                return (
                  <tr key={slot.id}>
                    <td>
                      <strong>{m?.name}</strong>
                    </td>
                    <td>{slot.date}</td>
                    <td>
                      {slot.time} ~ {finish(slot.time, m?.duration || 60)}
                      <small>{m?.duration}분 소요</small>
                    </td>
                    <td>
                      <del>{money(m?.original || 0)}</del>
                    </td>
                    <td>
                      <span className="b-badge">{slot.discount}%</span>
                    </td>
                    <td>
                      <strong>
                        {money(Math.round((m?.original || 0) * (1 - slot.discount / 100)))}
                      </strong>
                    </td>
                    <td>
                      <span
                        className={
                          'b-badge ' + (slot.status === 'selling' ? 'b-green' : 'b-neutral')
                        }
                      >
                        {STATUS[slot.status]}
                      </span>
                    </td>
                    <td>
                      {['selling', 'stopped'].includes(slot.status) ? (
                        <div className="b-flex">
                          <button
                            className="b-button b-small"
                            onClick={() => {
                              setEdit({ ...slot });
                              setError('');
                            }}
                          >
                            수정
                          </button>
                          <button className="b-button b-small" onClick={() => toggle(slot.id)}>
                            {slot.status === 'selling' ? '판매 중지' : '재공개'}
                          </button>
                        </div>
                      ) : (
                        <span className="b-muted">변경 불가</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {!list.length && <p className="b-empty">조건에 맞는 빈자리가 없습니다.</p>}
      </section>
      {notice && (
        <p className="b-success" role="status">
          {notice}
        </p>
      )}
      {draft && (
        <Dialog title="빈자리 등록 및 실시간 겹침 검사" onClose={close}>
          <form className="b-form-grid" onSubmit={submit}>
            <label className="b-span-two">
              연결 시술 *
              <select
                required
                value={draft.serviceId}
                onChange={(e) => update('serviceId', e.target.value)}
              >
                <option value="">선택해 주세요</option>
                {menus
                  .filter((m) => m.active)
                  .map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} ({m.duration}분)
                    </option>
                  ))}
              </select>
            </label>
            <label>
              등록 날짜 *
              <input
                type="date"
                min={DEMO_DATE}
                required
                value={draft.date}
                onChange={(e) => update('date', e.target.value)}
              />
            </label>
            <label>
              시작 시간 *
              <input
                type="time"
                min="10:00"
                max="21:00"
                step="1800"
                required
                value={draft.time}
                onChange={(e) => update('time', e.target.value)}
              />
            </label>
            <label>
              할인율 (%)
              <input
                type="number"
                min="0"
                max="70"
                required
                value={draft.discount}
                onChange={(e) => update('discount', e.target.value)}
              />
            </label>
            <div className="b-notice">
              <small>종료 시각 (자동)</small>
              <strong>{finish(draft.time || '00:00', menu?.duration || 60)}</strong>
              <small>
                현재 적용 가격{' '}
                {money(Math.round((menu?.original || 0) * (1 - Number(draft.discount) / 100)))}
              </small>
            </div>
            {error && (
              <p role="alert" className="b-error b-span-two">
                {error}
              </p>
            )}
            <button
              className="b-button b-primary b-span-two"
              disabled={!menus.some((m) => m.active)}
            >
              빈자리 저장하기
            </button>
          </form>
        </Dialog>
      )}
    </div>
  );
}
