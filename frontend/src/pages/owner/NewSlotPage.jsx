import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { DEMO_DATE, finish, money, validateSlot } from '@/utils/demoData';
import { useDesignStore } from '@/store/useDesignStore';
import useSlotTreatments from '@/hooks/useSlotTreatments';

export default function NewSlotPage() {
  const { menus, status, error: loadError, refreshMenus } = useSlotTreatments();
  const historyMenus = useDesignStore((s) => s.menus);
  const slots = useDesignStore((s) => s.slots);
  const bookings = useDesignStore((s) => s.bookings);
  const saveSlot = useDesignStore((s) => s.saveSlot);
  const navigate = useNavigate();
  const [serviceId, setServiceId] = useState(menus.find((m) => m.active)?.id || '');
  const [date, setDate] = useState(DEMO_DATE);
  const [time, setTime] = useState('14:00');
  const [scenario, setScenario] = useState('normal');
  const [error, setError] = useState('');
  const menu = menus.find((m) => m.id === serviceId);
  const discount = date === DEMO_DATE ? 35 : 25;
  const draft = { id: 'new-slot-preview', serviceId, date, time, discount, status: 'selling' };
  const validationMenus = [
    ...historyMenus.filter((item) => !menus.some((menu) => menu.id === item.id)),
    ...menus,
  ];
  const conflict = validateSlot(draft, validationMenus, slots, bookings);
  const price = Math.round((menu?.original || 0) * (1 - discount / 100));
  const changeScenario = (value) => {
    setScenario(value);
    setError('');
    setDate(DEMO_DATE);
    setTime(value === 'conflict' ? '15:30' : '14:00');
    setServiceId(menus.find((m) => m.active)?.id || '');
  };
  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const currentMenus = await refreshMenus();
      saveSlot({ ...draft, id: 'slot-' + Date.now() }, currentMenus);
      navigate(ROUTES.OWNER_SLOTS, { state: { slotCreated: true } });
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <div className="b-new-slot-page">
      <Link className="b-back" to={ROUTES.OWNER_SLOTS}>
        ← 빈자리 관리 목록으로 돌아가기
      </Link>
      <div className="b-row b-title-row">
        <div>
          <h1>
            빈자리 즉시 등록 <span className="b-badge">⚡ 실시간 동기화</span>
          </h1>
          <p className="b-muted">
            시술 메뉴와 일정을 선택하면 기존 예약과 중복 여부를 자동 검사하고 타임세일 빈자리로
            등록합니다.
          </p>
        </div>
        <div className="b-panel b-options b-new-slot-scenarios">
          <strong>검사 시뮬레이션:</strong>
          <button
            className={scenario === 'normal' ? 'is-selected' : ''}
            onClick={() => changeScenario('normal')}
          >
            정상 등록 가능 상태
          </button>
          <button
            className={scenario === 'conflict' ? 'is-selected' : ''}
            onClick={() => changeScenario('conflict')}
          >
            일정 중복 충돌 오류 예시
          </button>
        </div>
      </div>
      <form className="b-columns" onSubmit={submit}>
        <div className="b-stack">
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <h2>
                <span className="b-step-number">1</span> 시술 메뉴 선택
              </h2>
              <Link className="b-pink b-help" to={ROUTES.OWNER_MENUS}>
                ⊕ 신규 시술 등록하러 가기
              </Link>
            </div>
            <fieldset className="b-service-selection">
              <legend className="b-visually-hidden">빈자리에 연결할 시술</legend>
              {menus
                .filter((m) => m.active)
                .map((m) => (
                  <label
                    key={m.id}
                    className={'b-service-option ' + (serviceId === m.id ? 'is-selected' : '')}
                  >
                    <input
                      type="radio"
                      name="service"
                      required
                      value={m.id}
                      checked={serviceId === m.id}
                      onChange={() => {
                        setServiceId(m.id);
                        setError('');
                      }}
                    />
                    <span>
                      <strong>{m.name}</strong>
                      <small>
                        ◷ 소요 {m.duration}분 · 정상가 {money(m.original)}
                      </small>
                    </span>
                    <strong className="b-pink">{money(m.original)}</strong>
                  </label>
                ))}
            </fieldset>
            {status === 'loading' && <p role="status">시술을 불러오는 중입니다.</p>}
            {loadError && (
              <div role="alert">
                <p>{loadError.message}</p>
                <button
                  type="button"
                  className="b-button"
                  onClick={() => refreshMenus().catch(() => {})}
                >
                  다시 불러오기
                </button>
              </div>
            )}
            {status === 'succeeded' && !menus.length && (
              <p role="status">
                선택 가능한 활성 시술이 없습니다. 시술 메뉴에서 등록하거나 활성화해 주세요.
              </p>
            )}
            <div className="b-notice b-help">
              비활성 시술은 선택할 수 없습니다. 시술 관리 메뉴에서 활성화한 뒤 등록해 주세요.
            </div>
          </section>
          <section className="b-panel b-pad">
            <h2 className="b-panel-heading">
              <span className="b-step-number">2</span> 일정 및 시간대 설정
            </h2>
            <div className="b-three-grid b-slot-date-fields">
              <label>
                시술 날짜
                <input
                  type="date"
                  required
                  min={DEMO_DATE}
                  max="2025-11-24"
                  value={date}
                  onChange={(e) => {
                    setDate(e.target.value);
                    setError('');
                  }}
                />
                <small>시연 기준일부터 31일 이내 가능</small>
              </label>
              <label>
                시작 시각 (타임 피커)
                <input
                  type="time"
                  required
                  min="10:00"
                  max="20:30"
                  step="1800"
                  value={time}
                  onChange={(e) => {
                    setTime(e.target.value);
                    setError('');
                  }}
                />
                <small>30분 단위 설정</small>
              </label>
              <label>
                종료 시각 (자동 산출)
                <input readOnly value={finish(time || '00:00', menu?.duration || 0)} />
                <small>선택 시술 기준 {menu?.duration || 0}분 자동 계산</small>
              </label>
            </div>
            <div className="b-flex b-time-recommendations">
              <small>추천 빈 슬롯:</small>
              {['14:00', '16:30', '19:00'].map((value) => (
                <button
                  type="button"
                  key={value}
                  className={'b-button b-small ' + (time === value ? 'b-selected-outline' : '')}
                  onClick={() => {
                    setTime(value);
                    setError('');
                  }}
                >
                  {value} ~ {finish(value, menu?.duration || 0)}
                  {time === value && ' (현재)'}
                </button>
              ))}
            </div>
          </section>
          <div
            className={conflict ? 'b-slot-validation b-error' : 'b-slot-validation b-success'}
            role="status"
          >
            <span>{conflict ? '!' : '✓'}</span>
            <div>
              <strong>
                {conflict ? '등록 불가: 일정 또는 입력값 확인 필요' : '등록 가능: 스케줄 충돌 없음'}
              </strong>
              <p>
                {conflict ||
                  '선택하신 시간대에 기존 확정 예약이나 중복 대기 중인 빈자리가 없습니다. 등록 즉시 데모 빈자리 목록에 노출됩니다.'}
              </p>
            </div>
          </div>
        </div>
        <aside className="b-stack b-slot-summary">
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <h2>♧ 타임세일 자동 정산 미리보기</h2>
              <span className="b-badge">초임박 특가</span>
            </div>
            <div className="b-highlight">
              <strong>⚡ 당일 타임세일 자동 적용 구간</strong>
              <p>
                시연 할인 정책 {discount}%를 적용합니다. 실제 할인율은 서버 정책 연결 후 확정됩니다.
              </p>
            </div>
            <dl className="b-details">
              <div>
                <dt>정상 가격</dt>
                <dd>
                  <del>{money(menu?.original || 0)}</del>
                </dd>
              </div>
              <div className="b-pink">
                <dt>↓ 타임세일 할인 ({discount}%)</dt>
                <dd>-{money((menu?.original || 0) - price)}</dd>
              </div>
              <div>
                <dt>
                  <strong>예상 고객 결제액</strong>
                </dt>
                <dd className="b-final-price">{money(price)}</dd>
              </div>
              <div>
                <dt>노쇼 방지 선결제 예약금 (20%)</dt>
                <dd className="b-pink">{money(Math.round(price * 0.2))}</dd>
              </div>
              <div>
                <dt>매장 현장 결제 잔금 (80%)</dt>
                <dd>{money(price - Math.round(price * 0.2))}</dd>
              </div>
            </dl>
            <div className="b-notice b-help">
              최종 할인율과 결제 금액은 실제 고객 예약 확정 시점에 결정됩니다. 이 화면은 모의 정책
              예시입니다.
            </div>
          </section>
          <section className="b-panel b-pad">
            {error && (
              <p className="b-error" role="alert">
                {error}
              </p>
            )}
            <button
              className="b-button b-primary b-wide"
              type="submit"
              disabled={status !== 'succeeded' || !menu?.active || Boolean(conflict)}
            >
              ⚑ 빈자리 즉시 등록하기
            </button>
            <Link className="b-button b-wide" to={ROUTES.OWNER_SLOTS}>
              취소
            </Link>
            <p className="b-help">
              등록 시 데모 빈자리 목록에서 바로 확인할 수 있습니다. 실제 고객 알림은 발송되지
              않습니다.
            </p>
          </section>
        </aside>
      </form>
    </div>
  );
}
