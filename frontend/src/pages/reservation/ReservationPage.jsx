import { useState } from 'react';
import { generatePath, Link, useNavigate, useParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { DEMO_DATE, SERVICES, finish, minutes, money, overlaps } from '@/utils/demoData';
import { useDesignStore } from '@/store/useDesignStore';

export default function ReservationPage() {
  const { id } = useParams();
  const service = SERVICES.find((item) => item.id === id);
  const navigate = useNavigate();
  const bookings = useDesignStore((s) => s.bookings);
  const createHold = useDesignStore((s) => s.createHold);
  const [date, setDate] = useState('2025-10-22');
  const [time, setTime] = useState('14:00');
  const [month, setMonth] = useState(9);
  const [year, setYear] = useState(2025);
  const [mode, setMode] = useState('normal');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  if (!service)
    return (
      <div className="b-empty">
        <h1>시술을 찾을 수 없어요</h1>
        <Link to={ROUTES.HOME}>빈자리 탐색으로</Link>
      </div>
    );
  const duration = 90;
  const blocked = (value) =>
    mode === 'soldout' ||
    minutes(value) + duration > 1200 ||
    bookings.some(
      (b) =>
        b.status === 'confirmed' &&
        b.serviceId === id &&
        b.date === date &&
        overlaps(value, duration, b.time, b.duration),
    );
  const handleNext = async () => {
    setError('');
    if (!time) {
      setError('시작 시간을 선택해 주세요.');
      return;
    }
    if (blocked(time) || mode === 'conflict') {
      setError('선택한 시간은 다른 예약과 겹치거나 시술 시간이 부족합니다.');
      return;
    }
    setLoading(true);
    try {
      createHold(id, date, time, true);
      navigate(ROUTES.CHECKOUT);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };
  const changeMonth = (delta) => {
    const next = new Date(year, month + delta, 1);
    setMonth(next.getMonth());
    setYear(next.getFullYear());
    setTime('');
  };
  const first = new Date(year, month, 1).getDay();
  const days = new Date(year, month + 1, 0).getDate();
  const setScenario = (value) => {
    setMode(value);
    setError('');
    setTime(value === 'initial' || value === 'soldout' ? '' : '14:00');
  };
  return (
    <div className="b-container b-page b-reserve-page">
      <Link className="b-back" to={ROUTES.HOME}>
        ← 매장·시술 목록으로 돌아가기
      </Link>
      <section className="b-hero b-reserve-hero">
        <div>
          <span className="b-badge">일반 예약 모드 (원하는 시간에 맞춤 예약)</span>
          <h1>예약 날짜와 시간을 선택해 주세요</h1>
          <p>
            <strong>
              {service.shop} · {service.name}
            </strong>{' '}
            <span className="b-badge">시술 소요 시간 {duration}분</span>
          </p>
        </div>
        <div>
          <small>정상 시술가</small>
          <h2>{money(service.original)}</h2>
        </div>
      </section>
      <div className="b-columns">
        <div className="b-stack">
          <div className="b-panel b-steps">
            {['1. 시술 확인', '2. 날짜·시간 선택', '3. 임시 확보 (5분)', '4. 예약금 결제'].map(
              (step, i) => (
                <span key={step} className={i === 1 ? 'is-current' : ''}>
                  {step}
                </span>
              ),
            )}
          </div>
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <h2>날짜 선택</h2>
              <div className="b-flex">
                <button aria-label="이전 달" onClick={() => changeMonth(-1)}>
                  ‹
                </button>
                <strong>
                  {year}년 {month + 1}월
                </strong>
                <button aria-label="다음 달" onClick={() => changeMonth(1)}>
                  ›
                </button>
              </div>
            </div>
            <div className="b-calendar" role="group" aria-label="예약 날짜">
              {['일', '월', '화', '수', '목', '금', '토'].map((d) => (
                <small key={d}>{d}</small>
              ))}
              {Array.from({ length: first }, (_, i) => (
                <span key={'blank-' + i} />
              ))}
              {Array.from({ length: days }, (_, i) => {
                const day = i + 1;
                const value =
                  year +
                  '-' +
                  String(month + 1).padStart(2, '0') +
                  '-' +
                  String(day).padStart(2, '0');
                const closed = new Date(year, month, day).getDay() === 0;
                const past = value < '2025-10-22';
                return (
                  <button
                    key={value}
                    aria-pressed={date === value}
                    disabled={closed || past}
                    className={date === value ? 'is-selected' : ''}
                    onClick={() => {
                      setDate(value);
                      setTime('');
                      setError('');
                    }}
                  >
                    {day}
                    <small>{closed ? '휴무' : value === DEMO_DATE ? '시연일' : '•'}</small>
                  </button>
                );
              })}
            </div>
            <p className="b-help">
              선택한 날짜: <strong className="b-pink">{date}</strong> · 시연 기준 2025년 10월
            </p>
          </section>
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <h2>시작 시간 선택 (30분 단위)</h2>
              <span className="b-badge">매장 운영 10:00 ~ 20:00</span>
            </div>
            <div className="b-notice">
              실시간 충돌 검증 룰 적용 중 · 시술 종료 시각은 {duration}분 뒤로 자동 계산됩니다.
            </div>
            {['오전 (10:00 ~ 12:00)', '오후 (12:00 ~ 20:00)'].map((label, group) => (
              <div key={label} className="b-time-group">
                <p>{label}</p>
                <div className="b-time-grid">
                  {Array.from({ length: group ? 16 : 4 }, (_, i) => {
                    const value =
                      (group ? 12 : 10) + Math.floor(i / 2) + ':' + (i % 2 ? '30' : '00');
                    return (
                      <button
                        key={value}
                        disabled={blocked(value)}
                        aria-pressed={time === value}
                        className={time === value ? 'is-selected' : ''}
                        onClick={() => {
                          setTime(value);
                          setError('');
                        }}
                      >
                        {value}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </section>
          <div className="b-preview">
            <strong>화면 상태 시뮬레이터 (MVP 데모 인터랙션)</strong>
            <div className="b-options">
              {[
                ['normal', '1. 정상'],
                ['initial', '2. 미선택 초기 상태'],
                ['soldout', '3. 전체 마감 상태'],
                ['conflict', '4. 충돌/시간부족 에러'],
                ['loading', '5. 실시간 검증 로딩'],
              ].map(([value, label]) => (
                <button
                  key={value}
                  aria-pressed={mode === value}
                  className={mode === value ? 'is-selected' : ''}
                  onClick={() => setScenario(value)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
        <aside className="b-panel b-pad b-summary">
          <div className="b-eyebrow">GENERAL RESERVATION</div>
          <h2>선택 요약</h2>
          <dl className="b-details">
            <div>
              <dt>시술 매장</dt>
              <dd>{service.shop}</dd>
            </div>
            <div>
              <dt>선택 시술</dt>
              <dd>{service.name}</dd>
            </div>
            <div>
              <dt>선택 날짜</dt>
              <dd>{date}</dd>
            </div>
          </dl>
          <div className="b-highlight">
            <h3>일정 및 자동 계산 시간</h3>
            <div className="b-row">
              <div>
                <small>선택 시작 시간</small>
                <h2>{time || '미선택'}</h2>
              </div>
              <div>
                <small>자동 계산 종료 시간</small>
                <h2 className="b-pink">{time ? finish(time, duration) : '—'}</h2>
              </div>
            </div>
            <p>총 {duration}분 · 선택한 시각부터 시술이 진행됩니다.</p>
          </div>
          <dl className="b-details">
            <div>
              <dt>정규 시술 금액 (정상가)</dt>
              <dd>{money(service.original)}</dd>
            </div>
            <div className="b-notice">
              <dt>지금 결제할 예약금 (20%)</dt>
              <dd className="b-pink">{money(service.original * 0.2)}</dd>
            </div>
            <div>
              <dt>매장 방문 현장 잔금 (80%)</dt>
              <dd>{money(service.original * 0.8)}</dd>
            </div>
          </dl>
          {error && (
            <p className="b-error" role="alert">
              {error}
            </p>
          )}
          <button
            className="b-button b-primary b-wide"
            disabled={loading || mode === 'loading' || !time || mode === 'soldout'}
            onClick={handleNext}
          >
            {loading || mode === 'loading' ? '실시간 검증 중…' : '다음 (예약 가능 여부 확인)'}
          </button>
          <p className="b-help">매장 사정 취소 시 100% 모의 환불 및 보상 정책 적용</p>
        </aside>
      </div>
      <p className="b-help">
        타임세일 시간으로 빠르게 예약하려면{' '}
        <Link
          className="b-pink"
          to={generatePath(ROUTES.RESERVE, { id })}
          onClick={() => {
            setDate(DEMO_DATE);
            setTime(service.time);
          }}
        >
          해당 빈자리의 시간 선택
        </Link>
      </p>
    </div>
  );
}
