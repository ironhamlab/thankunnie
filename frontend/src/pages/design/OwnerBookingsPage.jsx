import { useState } from 'react';
import { SERVICES, STATUS, finish, money } from './data';
import { useDesignStore } from './useDesignStore';

export default function OwnerBookingsPage() {
  const bookings = useDesignStore((s) => s.bookings);
  const [selectedId, setSelectedId] = useState(bookings[0]?.id);
  const [status, setStatus] = useState('');
  const list = bookings.filter((b) => !status || b.status === status);
  const selected = list.find((b) => b.id === selectedId) || list[0];
  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>
            예약 관리 <span className="b-badge">실시간 동기화</span>
          </h1>
          <p className="b-muted">
            고객의 빈자리 모의 예약 및 결제 내역을 조회합니다. (내 매장 전용)
          </p>
        </div>
        <span className="b-demo-note">MVP 모의 결제 환경 · 실제 결제 없음</span>
      </div>
      <div className="b-panel b-pad b-row">
        <strong>예약 상태</strong>
        <div className="b-options">
          {[
            ['', '전체'],
            ['confirmed', '예약 확정'],
            ['completed', '방문 완료'],
            ['cancelled', '고객 취소'],
          ].map(([value, label]) => (
            <button
              key={value}
              className={status === value ? 'is-selected' : ''}
              onClick={() => setStatus(value)}
            >
              {label} ({bookings.filter((b) => !value || b.status === value).length})
            </button>
          ))}
        </div>
      </div>
      <div className="b-owner-bookings-grid">
        <section className="b-panel b-table-panel">
          <div className="b-row b-pad">
            <h2>예약 목록</h2>
            <small>실시간 {list.length}건 검색됨</small>
          </div>
          <div className="b-table-scroll">
            <table>
              <thead>
                <tr>
                  {[
                    '예약번호',
                    '예약자명',
                    '시술 메뉴',
                    '예약 일시',
                    '결제 상태',
                    '예약 상태',
                    '상세',
                  ].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {list.map((b) => (
                  <tr key={b.id} className={selected?.id === b.id ? 'is-active' : ''}>
                    <td>{b.id}</td>
                    <td>
                      <strong>{b.name}</strong>
                      <small>{b.phone}</small>
                    </td>
                    <td>{SERVICES.find((s) => s.id === b.serviceId)?.name}</td>
                    <td>
                      {b.date}
                      <small>
                        {b.time}~{finish(b.time, b.duration)}
                      </small>
                    </td>
                    <td>
                      <span className="b-badge b-green">
                        {b.status === 'cancelled' ? '모의 부분환불 완료' : '모의 결제 완료'}
                      </span>
                    </td>
                    <td>
                      <span className="b-badge">{STATUS[b.status]}</span>
                    </td>
                    <td>
                      <button className="b-button b-small" onClick={() => setSelectedId(b.id)}>
                        상세 보기
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!list.length && <p className="b-empty">해당 상태의 예약이 없습니다.</p>}
          <p className="b-table-foot">상세 보기 버튼을 누르면 우측 상세 내역 패널이 전환됩니다.</p>
        </section>
        {selected && (
          <aside className="b-stack">
            <section className="b-panel b-pad">
              <div className="b-row b-panel-heading">
                <h2>예약 상세</h2>
                <span className="b-badge">{STATUS[selected.status]}</span>
              </div>
              <p className="b-help">{selected.id}</p>
              <div className="b-notice">
                <h3>고객 정보</h3>
                <div className="b-row">
                  <strong>{selected.name}</strong>
                  <span>{selected.phone}</span>
                </div>
                <p>고객 요청사항</p>
                <div className="b-panel b-pad">{selected.request || '요청사항 없음'}</div>
              </div>
              <div className="b-notice">
                <h3>시술 정보</h3>
                <strong>{SERVICES.find((s) => s.id === selected.serviceId)?.name}</strong>
                <p className="b-pink">
                  {selected.date} {selected.time}~{finish(selected.time, selected.duration)}
                </p>
                <small>소요 시간 {selected.duration}분</small>
              </div>
              <h3>정산 및 모의 결제 내역</h3>
              <dl className="b-details">
                <div>
                  <dt>최종 시술 금액</dt>
                  <dd className="b-pink">{money(selected.price)}</dd>
                </div>
                <div>
                  <dt>모의 예약금 (20%)</dt>
                  <dd>{money(selected.deposit)}</dd>
                </div>
                <div>
                  <dt>현장 결제 잔금</dt>
                  <dd>{money(selected.price - selected.deposit)}</dd>
                </div>
                {selected.status === 'cancelled' && (
                  <div>
                    <dt>모의 환불 금액</dt>
                    <dd>{money(selected.refund || 0)}</dd>
                  </div>
                )}
              </dl>
              <div className="b-notice">
                모의 환경 안내: 실제 카드 조회, 수동 결제, 강제 환불은 제공되지 않습니다.
              </div>
            </section>
          </aside>
        )}
      </div>
    </div>
  );
}
