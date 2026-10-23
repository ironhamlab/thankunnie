import { useState } from 'react';
import { useDesignStore } from './useDesignStore';
import { money } from './data';
import Dialog from './Dialog';

export default function MenusPage() {
  const menus = useDesignStore((s) => s.menus);
  const save = useDesignStore((s) => s.saveMenu);
  const toggle = useDesignStore((s) => s.toggleMenu);
  const [edit, setEdit] = useState(null);
  const [empty, setEmpty] = useState(false);
  const [notice, setNotice] = useState('');
  const submit = (event) => {
    event.preventDefault();
    if (!edit.name.trim()) return;
    save({
      ...edit,
      name: edit.name.trim(),
      duration: Number(edit.duration),
      original: Number(edit.original),
    });
    setEdit(null);
    setEmpty(false);
    setNotice('시술 메뉴를 저장했습니다.');
  };
  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>
            시술 메뉴 관리 <span className="b-badge">{menus.length}개 등록됨</span>
          </h1>
          <p className="b-muted">등록된 시술 메뉴를 관리하고 신규 빈자리 등록 시 연결합니다.</p>
        </div>
        <div className="b-flex">
          <button className="b-button" onClick={() => setEmpty(!empty)}>
            {empty ? '목록으로 돌아가기' : '빈 목록 미리보기'}
          </button>
          <button
            className="b-button b-primary"
            onClick={() =>
              setEdit({
                id: 'menu-' + Date.now(),
                name: '',
                description: '',
                duration: 60,
                original: 50000,
                active: true,
              })
            }
          >
            + 신규 시술 등록
          </button>
        </div>
      </div>
      <div className="b-notice">
        <strong>시술 정보 변경 유의사항</strong>
        <p>
          시술 정보(정상가, 소요시간 등)를 변경해도 이미 확정된 기존 고객 예약에는 소급 적용되지
          않습니다. 변경된 내용은 향후 생성되는 신규 빈자리부터 반영됩니다.
        </p>
      </div>
      <div className="b-highlight">
        본 화면은 MVP 매장 시술 메뉴 단일화 모드입니다. 직원별 전담 메뉴 및 옵션별 추가금 기능은
        제외됩니다.
      </div>
      <section className="b-panel b-table-panel">
        <div className="b-row b-pad">
          <h2>시술 목록</h2>
          <span className="b-badge b-green">활성 {menus.filter((m) => m.active).length}건</span>
        </div>
        {empty ? (
          <div className="b-empty">
            <h2>등록된 시술이 없어요</h2>
            <p>신규 시술을 등록하고 빈자리를 연결해 주세요.</p>
          </div>
        ) : (
          <div className="b-table-scroll">
            <table>
              <thead>
                <tr>
                  {['시술명', '설명 요약', '소요 시간', '정상가', '상태', '관리'].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {menus.map((m) => (
                  <tr key={m.id}>
                    <td>
                      <strong>{m.name}</strong>
                    </td>
                    <td>{m.description || '상세 설명 없음'}</td>
                    <td>
                      <span className="b-badge b-neutral">{m.duration}분</span>
                    </td>
                    <td>
                      <strong>{money(m.original)}</strong>
                    </td>
                    <td>
                      <span className={'b-badge ' + (m.active ? 'b-green' : 'b-neutral')}>
                        {m.active ? '● 활성' : '비활성'}
                      </span>
                    </td>
                    <td>
                      <div className="b-flex">
                        <button className="b-button b-small" onClick={() => setEdit({ ...m })}>
                          수정
                        </button>
                        <button className="b-button b-small" onClick={() => toggle(m.id)}>
                          {m.active ? '비활성화' : '활성화'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="b-table-foot">
          팁: 비활성화된 시술 메뉴는 신규 빈자리 등록 시 선택 목록에 노출되지 않습니다.
        </p>
      </section>
      {notice && (
        <p className="b-success" role="status">
          {notice}
        </p>
      )}
      {edit && (
        <Dialog
          title={menus.some((m) => m.id === edit.id) ? '시술 메뉴 수정' : '신규 시술 등록'}
          onClose={() => setEdit(null)}
        >
          <form onSubmit={submit} className="b-form-grid">
            <label className="b-span-two">
              시술명 *
              <input
                autoFocus
                required
                maxLength={80}
                value={edit.name}
                onChange={(e) => setEdit({ ...edit, name: e.target.value })}
              />
            </label>
            <label>
              소요 시간 (분) *
              <input
                type="number"
                min="10"
                max="360"
                step="5"
                required
                value={edit.duration}
                onChange={(e) => setEdit({ ...edit, duration: e.target.value })}
              />
            </label>
            <label>
              정상가 (원) *
              <input
                type="number"
                min="1000"
                max="10000000"
                step="100"
                required
                value={edit.original}
                onChange={(e) => setEdit({ ...edit, original: e.target.value })}
              />
            </label>
            <label className="b-span-two">
              설명 요약
              <textarea
                rows="3"
                maxLength={200}
                value={edit.description || ''}
                onChange={(e) => setEdit({ ...edit, description: e.target.value })}
              />
            </label>
            <button className="b-button b-primary b-span-two">저장</button>
          </form>
        </Dialog>
      )}
    </div>
  );
}
