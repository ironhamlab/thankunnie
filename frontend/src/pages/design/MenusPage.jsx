import { useEffect, useState } from 'react';
import { useTreatmentStore } from '@/store/useTreatmentStore';
import { money } from './data';
import TreatmentForm from './TreatmentForm';
import './treatment.css';

export default function MenusPage() {
  const treatments = useTreatmentStore((state) => state.treatments);
  const categories = useTreatmentStore((state) => state.categories);
  const status = useTreatmentStore((state) => state.status);
  const error = useTreatmentStore((state) => state.error);
  const isSaving = useTreatmentStore((state) => state.mutationStatus === 'loading');
  const load = useTreatmentStore((state) => state.loadTreatments);
  const save = useTreatmentStore((state) => state.saveTreatment);
  const changeStatus = useTreatmentStore((state) => state.changeTreatmentStatus);
  const [edit, setEdit] = useState(null);
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState(null);

  useEffect(() => {
    load();
  }, [load]);

  const openForm = (treatment) => {
    setNotice('');
    setActionError(null);
    setEdit({ treatment });
  };
  const submit = async (treatmentId, payload) => {
    setActionError(null);
    try {
      await save(treatmentId, payload);
      setEdit(null);
      setNotice('시술 메뉴를 저장했습니다.');
    } catch (failure) {
      setActionError(failure);
    }
  };
  const toggle = async (treatment) => {
    setNotice('');
    setActionError(null);
    try {
      const updated = await changeStatus(
        treatment.treatmentId,
        treatment.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      );
      setNotice(
        `${updated.name} 시술을 ${updated.status === 'ACTIVE' ? '활성화' : '비활성화'}했습니다.`,
      );
    } catch (failure) {
      setActionError(failure);
    }
  };

  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>
            시술 메뉴 관리 <span className="b-badge">{treatments.length}개 등록됨</span>
          </h1>
          <p className="b-muted">시술명, 설명, 카테고리, 정상가와 소요 시간을 관리합니다.</p>
        </div>
        <button
          className="b-button b-primary"
          disabled={status !== 'succeeded' || !categories.length || isSaving}
          onClick={() => openForm(null)}
        >
          + 신규 시술 등록
        </button>
      </div>
      <div className="b-notice">
        <strong>시술 정보 변경 유의사항</strong>
        <p>
          비활성 시술은 신규 빈자리 선택에서 제외됩니다. 기존 빈자리와 예약 이력은 유지됩니다.
          소요 시간은 30분 배수로 입력해 주세요.
        </p>
      </div>
      {(status === 'idle' || status === 'loading') && (
        <p role="status">시술 메뉴를 불러오는 중입니다.</p>
      )}
      {status === 'failed' && (
        <div role="alert">
          <p>{error.message}</p>
          <button className="b-button" onClick={load}>
            다시 불러오기
          </button>
        </div>
      )}
      {status === 'succeeded' && (
        <>
          {!categories.length && (
            <p role="alert">
              선택 가능한 카테고리가 없습니다. 관리자에게 카테고리 등록을 요청해 주세요.
            </p>
          )}
          <section className="b-panel b-table-panel" aria-busy={isSaving}>
            <div className="b-row b-pad">
              <h2>시술 목록</h2>
              <span className="b-badge b-green">
                활성 {treatments.filter((item) => item.status === 'ACTIVE').length}건
              </span>
            </div>
            {!treatments.length ? (
              <div className="b-empty">
                <h2>등록된 시술이 없어요</h2>
                <p>신규 시술을 등록해 주세요.</p>
              </div>
            ) : (
              <div className="b-table-scroll">
                <table>
                  <thead>
                    <tr>
                      {['시술명', '카테고리', '설명', '소요 시간', '정상가', '상태', '관리'].map(
                        (heading) => (
                          <th key={heading} scope="col">
                            {heading}
                          </th>
                        ),
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {treatments.map((item) => (
                      <tr key={item.treatmentId}>
                        <td>
                          <strong>{item.name}</strong>
                          <small>{item.treatmentType === 'MODEL' ? '모델 모집' : '일반 시술'}</small>
                          {item.treatmentType === 'MODEL' && (
                            <small>참가비 {item.participationFee == null ? '미등록' : money(item.participationFee)}</small>
                          )}
                        </td>
                        <td>{item.categoryName}</td>
                        <td>{item.description || '상세 설명 없음'}</td>
                        <td>{item.durationMinutes}분</td>
                        <td>
                          <strong>{money(item.regularPrice)}</strong>
                        </td>
                        <td>{item.status === 'ACTIVE' ? '활성' : '비활성'}</td>
                        <td>
                          <div className="b-flex">
                            <button
                              className="b-button b-small"
                              disabled={isSaving}
                              onClick={() => openForm(item)}
                            >
                              수정
                            </button>
                            <button
                              className="b-button b-small"
                              disabled={isSaving}
                              onClick={() => toggle(item)}
                            >
                              {item.status === 'ACTIVE' ? '비활성화' : '활성화'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
      {notice && (
        <p className="b-success" role="status">
          {notice}
        </p>
      )}
      {!edit && actionError && <p role="alert">{actionError.message}</p>}
      {edit && (
        <TreatmentForm
          treatment={edit.treatment}
          categories={categories}
          isSaving={isSaving}
          error={actionError}
          onSave={submit}
          onClose={() => {
            if (!isSaving) setEdit(null);
          }}
        />
      )}
    </div>
  );
}
