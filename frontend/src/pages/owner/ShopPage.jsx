import { useEffect, useState } from 'react';
import { useShopStore } from '@/store/useShopStore';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Alert from '@/components/common/Alert';
import EmptyState from '@/components/common/EmptyState';
import OwnerImageEditor from './components/OwnerImageEditor';

const FIELDS = [
  ['name', '매장명', 100],
  ['phone', '대표 연락처', 20],
  ['regionCode', '지역 코드', 20],
  ['address', '주소', 300],
  ['addressDetail', '상세 주소', 200],
];

function ShopForm({ shop, isSaving, onSave, onImage }) {
  const [form, setForm] = useState(shop);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const change = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
    setMessage('');
  };
  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setMessage('');
    const {
      categoryId,
      name,
      regionCode,
      address,
      addressDetail,
      latitude,
      longitude,
      phone,
      description,
      notice,
    } = form;
    try {
      const saved = await onSave({
        categoryId: Number(categoryId),
        name: name.trim(),
        regionCode: regionCode.trim(),
        address: address.trim(),
        addressDetail,
        latitude: Number(latitude),
        longitude: Number(longitude),
        phone: phone.trim(),
        description,
        notice,
      });
      setForm(saved);
      setMessage('매장 정보를 저장했습니다.');
    } catch (failure) {
      setError(failure.message);
    }
  };
  return (
    <div className="b-stack">
      <h1>매장 관리</h1>
      <Panel padded>
        <form onSubmit={submit} className="b-stack">
          <fieldset disabled={isSaving} className="b-form-grid">
            {FIELDS.map(([name, label, maxLength]) => (
              <label key={name}>
                {label}
                {name !== 'addressDetail' && ' *'}
                <input
                  name={name}
                  value={form[name] ?? ''}
                  maxLength={maxLength}
                  required={name !== 'addressDetail'}
                  onChange={change}
                />
              </label>
            ))}
            <label>
              업종 카테고리 *
              <select name="categoryId" value={form.categoryId} onChange={change} required>
                {[
                  [1, '네일'],
                  [2, '헤어'],
                  [3, '속눈썹'],
                  [4, '왁싱'],
                  [5, '바버'],
                ].map(([id, label]) => (
                  <option key={id} value={id}>
                    {label}
                  </option>
                ))}
                {![1, 2, 3, 4, 5].includes(Number(form.categoryId)) && (
                  <option value={form.categoryId}>현재 업종 ({form.categoryId})</option>
                )}
              </select>
            </label>
            {['latitude', 'longitude'].map((name) => (
              <label key={name}>
                {name === 'latitude' ? '위도' : '경도'} *
                <input
                  type="number"
                  name={name}
                  value={form[name] ?? ''}
                  min={name === 'latitude' ? -90 : -180}
                  max={name === 'latitude' ? 90 : 180}
                  step="0.0000001"
                  required
                  onChange={change}
                />
              </label>
            ))}
            {[
              ['description', '매장 소개'],
              ['notice', '매장 공지'],
            ].map(([name, label]) => (
              <label key={name} className="b-span-two">
                {label}
                <textarea
                  name={name}
                  value={form[name] ?? ''}
                  maxLength={2000}
                  rows="4"
                  onChange={change}
                />
              </label>
            ))}
          </fieldset>
          {error && (
            <Alert tone="error" role="alert">
              {error}
            </Alert>
          )}
          {message && (
            <Alert tone="success" role="status">
              {message}
            </Alert>
          )}
          <div className="b-flex">
            <Button
              type="button"
              disabled={isSaving}
              onClick={() => {
                setForm(shop);
                setError('');
                setMessage('변경사항을 취소했습니다.');
              }}
            >
              취소
            </Button>
            <Button type="submit" variant="primary" disabled={isSaving}>
              {isSaving ? '저장 중…' : '변경사항 저장'}
            </Button>
          </div>
        </form>
      </Panel>
      <Panel padded>
        <h2>매장 대표 이미지</h2>
        <OwnerImageEditor imageUrl={shop.imageUrl} isSaving={isSaving} onSave={onImage} />
      </Panel>
    </div>
  );
}

export default function ShopPage() {
  const shop = useShopStore((state) => state.shop);
  const status = useShopStore((state) => state.status);
  const error = useShopStore((state) => state.error);
  const isSaving = useShopStore((state) => state.mutationStatus === 'loading');
  const load = useShopStore((state) => state.loadShop);
  const save = useShopStore((state) => state.saveShop);
  const saveImage = useShopStore((state) => state.saveImage);
  useEffect(() => {
    load();
  }, [load]);
  if (status === 'failed')
    return (
      <Alert tone="error" role="alert">
        {error.message} <Button onClick={load}>다시 불러오기</Button>
      </Alert>
    );
  if (!shop) return <EmptyState role="status">매장 정보를 불러오는 중입니다.</EmptyState>;
  return (
    <ShopForm key={shop.shopId} shop={shop} isSaving={isSaving} onSave={save} onImage={saveImage} />
  );
}
