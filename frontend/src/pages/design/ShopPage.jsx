import { useState } from 'react';
import { useDesignStore } from './useDesignStore';
import Asset from './Asset';

export default function ShopPage() {
  const shop = useDesignStore((s) => s.shop);
  const save = useDesignStore((s) => s.saveShop);
  const [form, setForm] = useState({ ...shop });
  const [status, setStatus] = useState('');
  const [scenario, setScenario] = useState('complete');
  const change = (e) => {
    setStatus('');
    setForm({ ...form, [e.target.name]: e.target.value });
  };
  const submit = (e) => {
    e.preventDefault();
    if (!form.name.trim() || !form.address.trim() || !form.phone.trim()) {
      setStatus('필수 입력 항목을 확인해 주세요.');
      return;
    }
    save(form);
    setStatus('변경사항을 저장했습니다. 새로고침 전까지 데모 화면에 유지됩니다.');
  };
  return (
    <div className="b-stack">
      <div className="b-notice">
        <strong>본 환경은 사장님 전용 테스트 모드이며, 모의 결제 기반으로 운영됩니다.</strong>
        <p>실제 고객 예약금 결제 및 정산 시스템은 영업 인증 완료 후 활성화됩니다.</p>
      </div>
      <div className="b-panel b-pad b-row">
        <strong>화면 상태 시연 컨트롤러</strong>
        <div className="b-options">
          {[
            ['complete', '매장 정보 등록 완료 상태'],
            ['error', '필수값 누락 오류 상태'],
            ['new', '신규 매장 등록 안내 모드'],
          ].map(([value, label]) => (
            <button
              key={value}
              className={scenario === value ? 'is-selected' : ''}
              onClick={() => {
                setScenario(value);
                setForm(
                  value === 'new'
                    ? {
                        name: '',
                        category: '네일·패디',
                        phone: '',
                        address: '',
                        detail: '',
                        description: '',
                      }
                    : { ...shop, name: value === 'error' ? '' : shop.name },
                );
                setStatus(value === 'error' ? '매장명은 필수 입력 항목입니다.' : '');
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </div>
      <form onSubmit={submit} className="b-columns">
        <div className="b-stack">
          <section className="b-panel b-pad">
            <h2 className="b-panel-heading">
              매장 기본 정보 <small className="b-pink">* 필수 입력 항목</small>
            </h2>
            <div className="b-form-grid">
              <label className="b-span-two">
                매장명 *
                <input name="name" value={form.name} maxLength={30} required onChange={change} />
              </label>
              <label>
                업종 카테고리 *
                <select name="category" value={form.category} onChange={change}>
                  {['네일·패디', '헤어', '속눈썹', '피부관리', '왁싱'].map((v) => (
                    <option key={v}>{v}</option>
                  ))}
                </select>
              </label>
              <label>
                대표 연락처 *
                <input name="phone" type="tel" value={form.phone} required onChange={change} />
              </label>
              <label className="b-span-two">
                지역 및 상세 주소 *
                <input name="address" value={form.address} required onChange={change} />
                <input name="detail" value={form.detail} aria-label="상세 주소" onChange={change} />
                <small>고객이 방문하기 쉽도록 출구 번호나 주변 랜드마크를 함께 적어주세요.</small>
              </label>
              <label className="b-span-two">
                매장 소개 텍스트 <small>{form.description.length}/500자</small>
                <textarea
                  name="description"
                  rows="6"
                  maxLength={500}
                  value={form.description}
                  onChange={change}
                />
              </label>
            </div>
          </section>
          <section className="b-panel b-pad">
            <h2 className="b-panel-heading">운영 시간 및 예약 정책 요약</h2>
            <div className="b-three-grid">
              {[
                ['정기 영업 시간', '월-토 11:00 ~ 21:00', '매주 일요일 정기 휴무'],
                ['빈자리 예약 마감 기준', '시술 시작 30분 전까지', '실시간 자동 확정'],
                ['노쇼 방지 예약금', '기본 시술가의 20%', '현장 결제 시 차감'],
              ].map(([label, value, note]) => (
                <div key={label} className="b-notice">
                  <small>{label}</small>
                  <strong>{value}</strong>
                  <small className="b-pink">{note}</small>
                </div>
              ))}
            </div>
          </section>
          <div className="b-panel b-pad b-row">
            <span className={scenario === 'error' ? 'b-error' : 'b-muted'} role="status">
              {status || '모든 변경사항이 안전하게 보관되어 있습니다.'}
            </span>
            <div className="b-flex">
              <button
                type="button"
                className="b-button"
                onClick={() => {
                  setForm({ ...shop });
                  setStatus('변경사항을 취소했습니다.');
                }}
              >
                취소
              </button>
              <button className="b-button b-primary" type="submit">
                변경사항 저장
              </button>
            </div>
          </div>
        </div>
        <aside className="b-stack">
          <section className="b-panel b-pad">
            <h2 className="b-panel-heading">
              매장 대표 이미지 <span className="b-badge b-neutral">자동 매칭</span>
            </h2>
            <div className="b-highlight">
              업로드 기능은 미지원하며 기본 매장 이미지가 자동 적용됩니다.
            </div>
            <Asset
              className="b-shop-image"
              name="12-2061-0.png"
              alt="프리미엄 네일 살롱 대표 이미지"
            />
            <p className="b-help">공식 테마 이미지 · 네일 살롱 A형</p>
          </section>
          <section className="b-panel b-pad">
            <h2 className="b-panel-heading">고객 앱 카드 노출 미리보기</h2>
            <Asset className="b-shop-image" name="12-2061-1.png" alt="네일 시술 예시" />
            <h2>{form.name || '매장명을 입력해 주세요'}</h2>
            <p>{form.address}</p>
            <div className="b-row">
              <strong className="b-pink">35% 42,000원</strong>
              <del className="b-muted">65,000원</del>
            </div>
            <div className="b-notice">수정된 내용은 저장 후 데모 화면에 반영됩니다.</div>
          </section>
        </aside>
      </form>
    </div>
  );
}
