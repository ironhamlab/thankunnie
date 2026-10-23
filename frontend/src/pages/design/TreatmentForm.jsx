import { useState } from 'react';
import Dialog from './Dialog';

export default function TreatmentForm({ treatment, categories, isSaving, error, onSave, onClose }) {
  const [form, setForm] = useState(
    treatment ?? {
      name: '',
      description: '',
      regularPrice: 50000,
      durationMinutes: 60,
      categoryId: '',
    },
  );
  const [validationError, setValidationError] = useState('');
  const change = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
  };
  const submit = (event) => {
    event.preventDefault();
    setValidationError('');
    if (!form.name.trim()) {
      setValidationError('시술명을 입력해 주세요.');
      return;
    }
    onSave(form.treatmentId, {
      name: form.name.trim(),
      description: form.description,
      regularPrice: Number(form.regularPrice),
      categoryId: Number(form.categoryId),
      durationMinutes: Number(form.durationMinutes),
    });
  };

  return (
    <Dialog title={treatment ? '시술 메뉴 수정' : '신규 시술 등록'} onClose={onClose}>
      <form onSubmit={submit} className="b-form-grid">
        <fieldset disabled={isSaving} className="treatment-fields b-span-two">
          <label className="b-span-two">
            시술명 *
            <input
              autoFocus
              required
              maxLength={100}
              name="name"
              value={form.name}
              onChange={change}
            />
          </label>
          <label className="b-span-two">
            카테고리 *
            <select required name="categoryId" value={form.categoryId} onChange={change}>
              <option value="">카테고리를 선택해 주세요</option>
              {categories.map((category) => (
                <option key={category.categoryId} value={category.categoryId}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            소요 시간 (분) *
            <input
              type="number"
              min="30"
              max="9990"
              step="30"
              required
              name="durationMinutes"
              value={form.durationMinutes}
              onChange={change}
            />
            <small>30분 단위로 입력해 주세요.</small>
          </label>
          <label>
            정상가 (원) *
            <input
              type="number"
              min="100"
              max="2147483600"
              step="100"
              required
              name="regularPrice"
              value={form.regularPrice}
              onChange={change}
            />
          </label>
          <label className="b-span-two">
            설명
            <textarea
              rows="3"
              maxLength={2000}
              name="description"
              value={form.description ?? ''}
              onChange={change}
            />
          </label>
        </fieldset>
        {(validationError || error) && (
          <p className="b-span-two" role="alert">
            {validationError || error.message}
          </p>
        )}
        <button className="b-button b-primary b-span-two" disabled={isSaving}>
          {isSaving ? '저장 중…' : '저장'}
        </button>
      </form>
    </Dialog>
  );
}
