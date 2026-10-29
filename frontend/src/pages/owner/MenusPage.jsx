import { useEffect, useState } from 'react';
import { useTreatmentStore } from '@/store/useTreatmentStore';
import { money } from '@/utils/demoData';
import TreatmentForm from '@/pages/owner/components/TreatmentForm';
import TreatmentThumbnail from './components/TreatmentThumbnail';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';

export default function MenusPage() {
  const treatments = useTreatmentStore((state) => state.treatments);
  const status = useTreatmentStore((state) => state.status);
  const error = useTreatmentStore((state) => state.error);
  const isMutating = useTreatmentStore((state) => state.mutationStatus === 'loading');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isSaving = isMutating || isSubmitting;
  const load = useTreatmentStore((state) => state.loadTreatments);
  const save = useTreatmentStore((state) => state.saveTreatment);
  const changeStatus = useTreatmentStore((state) => state.changeTreatmentStatus);
  const saveImage = useTreatmentStore((state) => state.saveImage);
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
  const submit = async (treatmentId, payload, imageFile) => {
    if (isSaving) return;
    setIsSubmitting(true);
    setActionError(null);
    let saved;
    try {
      saved = await save(treatmentId, payload);
      if (imageFile) await saveImage(saved.treatmentId, imageFile);
      setEdit(null);
      setNotice('시술 메뉴를 저장했습니다.');
    } catch (failure) {
      if (saved) {
        // 생성된 ID를 유지해 이미지 재시도가 새 시술을 중복 생성하지 않도록 한다.
        setEdit({ treatment: saved });
        setActionError({
          ...failure,
          message: `시술 정보는 저장됐지만 이미지 등록에 실패했습니다. 다시 저장해 주세요. ${failure.message}`,
        });
      } else setActionError(failure);
    } finally {
      setIsSubmitting(false);
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
            시술 메뉴 관리 <Badge>{treatments.length}개 등록됨</Badge>
          </h1>
          <p className="b-muted">시술명, 설명, 정상가와 소요 시간을 관리합니다.</p>
        </div>
        <Button
          variant="primary"
          disabled={status !== 'succeeded' || isSaving}
          onClick={() => openForm(null)}
        >
          + 신규 시술 등록
        </Button>
      </div>
      <Notice>
        <strong>시술 정보 변경 유의사항</strong>
        <p>
          비활성 시술은 신규 빈자리 선택에서 제외됩니다. 기존 빈자리와 예약 이력은 유지됩니다. 소요
          시간은 30분 배수로 입력해 주세요.
        </p>
      </Notice>
      {(status === 'idle' || status === 'loading') && (
        <p role="status">시술 메뉴를 불러오는 중입니다.</p>
      )}
      {status === 'failed' && (
        <div role="alert">
          <p>{error.message}</p>
          <Button onClick={load}>다시 불러오기</Button>
        </div>
      )}
      {status === 'succeeded' && (
        <>
          <Panel as="section" className="b-table-panel" aria-busy={isSaving}>
            <div className="b-row b-pad">
              <h2>시술 목록</h2>
              <Badge tone="success">
                활성 {treatments.filter((item) => item.status === 'ACTIVE').length}건
              </Badge>
            </div>
            {!treatments.length ? (
              <EmptyState>
                <h2>등록된 시술이 없어요</h2>
                <p>신규 시술을 등록해 주세요.</p>
              </EmptyState>
            ) : (
              <div className="b-table-scroll">
                <table>
                  <thead>
                    <tr>
                      {['이미지', '시술명', '설명', '소요 시간', '정상가', '상태', '관리'].map(
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
                          <TreatmentThumbnail
                            key={item.imageUrl ?? 'default'}
                            imageUrl={item.imageUrl}
                            name={item.name}
                          />
                        </td>
                        <td>
                          <strong>{item.name}</strong>
                          <small>
                            {item.treatmentType === 'MODEL' ? '모델 모집' : '일반 시술'}
                          </small>
                        </td>
                        <td>{item.description || '상세 설명 없음'}</td>
                        <td>{item.durationMinutes}분</td>
                        <td>
                          <strong>{money(item.regularPrice)}</strong>
                        </td>
                        <td>{item.status === 'ACTIVE' ? '활성' : '비활성'}</td>
                        <td>
                          <div className="b-flex">
                            <Button size="small" disabled={isSaving} onClick={() => openForm(item)}>
                              수정
                            </Button>
                            <Button size="small" disabled={isSaving} onClick={() => toggle(item)}>
                              {item.status === 'ACTIVE' ? '비활성화' : '활성화'}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>
        </>
      )}
      {notice && (
        <Alert as="p" tone="success" role="status">
          {notice}
        </Alert>
      )}
      {!edit && actionError && <p role="alert">{actionError.message}</p>}
      {edit && (
        <TreatmentForm
          treatment={edit.treatment}
          imageUrl={
            treatments.find((item) => item.treatmentId === edit.treatment?.treatmentId)?.imageUrl
          }
          onImageSave={(file) => saveImage(edit.treatment.treatmentId, file)}
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
