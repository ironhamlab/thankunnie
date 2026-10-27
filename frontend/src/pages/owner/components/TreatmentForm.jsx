import { useState } from 'react';
import Dialog from '@/components/common/Dialog';

export default function TreatmentForm({ treatment, categories, isSaving, error, onSave, onClose }) {
  const [form, setForm] = useState(
    {
      name: '',
      description: '',
      regularPrice: 50000,
      price: 50000,
      lastMinutePrice: 50000,
      durationMinutes: 60,
      categoryId: '',
      treatmentType: 'NORMAL',
      modelCondition: '',
      photoNotice: '',
      ...treatment,
    },
  );
  const [validationError, setValidationError] = useState('');
  const change = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({
      ...previous,
      ...(name === 'regularPrice' ? {
        price: String(previous.price) === String(previous.regularPrice) ? value : previous.price,
        lastMinutePrice: String(previous.lastMinutePrice) === String(previous.regularPrice)
          ? value : previous.lastMinutePrice,
      } : {}),
      [name]: value,
    }));
  };
  const submit = (event) => {
    event.preventDefault();
    setValidationError('');
    if (!form.name.trim()) {
      setValidationError('시술명을 입력해 주세요.');
      return;
    }
    const prices = [form.regularPrice, form.price, form.lastMinutePrice].map(Number);
    if (prices.some((price) => !Number.isInteger(price) || price <= 0 || price % 100 !== 0)
      || prices[2] > prices[1] || prices[1] > prices[0]) {
      setValidationError('가격은 100원 단위로 초임박가 ≤ 판매가 ≤ 정상가 순서로 입력해 주세요.');
      return;
    }
    const isModel = form.treatmentType === 'MODEL';
    if (isModel && (!form.modelCondition?.trim() || !form.photoNotice?.trim())) {
      setValidationError('모델 모집은 참여 조건과 촬영 안내가 필수입니다.');
      return;
    }
    onSave(form.treatmentId, {
      name: form.name.trim(),
      description: form.description,
      regularPrice: Number(form.regularPrice),
      price: Number(form.price),
      lastMinutePrice: Number(form.lastMinutePrice),
      categoryId: Number(form.categoryId),
      durationMinutes: Number(form.durationMinutes),
      conditions: {
        treatmentType: form.treatmentType,
        modelCondition: isModel ? form.modelCondition.trim() : null,
        photoNotice: isModel ? form.photoNotice.trim() : null,
      },
    });
  };

  return (
    <Dialog title={treatment ? '시술 메뉴 수정' : '신규 시술 등록'} onClose={onClose}>
      <form onSubmit={submit} className="b-form-grid">
        <fieldset disabled={isSaving} className="treatment-fields b-span-two">
          <label className="b-span-two">
            목적 태그 *
            <select required name="treatmentType" value={form.treatmentType} onChange={change}>
              <option value="NORMAL">일반 시술</option>
              <option value="MODEL">모델 모집</option>
            </select>
          </label>
          {form.treatmentType === 'MODEL' && (
            <>
              <label className="b-span-two">
                참여 조건 *
                <textarea name="modelCondition" required maxLength={2000} rows="3"
                  value={form.modelCondition ?? ''} onChange={change} />
              </label>
              <label className="b-span-two">
                촬영 안내 *
                <textarea name="photoNotice" required maxLength={1000} rows="3"
                  value={form.photoNotice ?? ''} onChange={change} />
              </label>
            </>
          )}
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
          <label>
            판매가 (원) *
            <input type="number" min="100" max={form.regularPrice} step="100" required
              name="price" value={form.price} onChange={change} />
          </label>
          <label>
            초임박가 (원) *
            <input type="number" min="100" max={form.price} step="100" required
              name="lastMinutePrice" value={form.lastMinutePrice} onChange={change} />
            <small>초임박가 ≤ 판매가 ≤ 정상가 · 같은 가격이면 할인 없음</small>
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
