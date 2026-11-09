import { useEffect, useRef, useState } from 'react';
import { canGeocode, geocodeAddress } from '@/utils/geocodeAddress';
import { useShopStore } from '@/store/useShopStore';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Alert from '@/components/common/Alert';
import EmptyState from '@/components/common/EmptyState';
import OwnerImageEditor from './components/OwnerImageEditor';

const FIELDS = [
  ['name', '매장명', 100],
  ['phone', '대표 연락처', 20],
  ['address', '주소', 300],
  ['addressDetail', '상세 주소', 200],
];

function ShopForm({ shop, isSaving, onSave, onImage }) {
  const [form, setForm] = useState(shop);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [isLocating, setIsLocating] = useState(false);
  // 이미지 선택·삭제는 대기만 하다가 변경사항 저장 시 함께 반영한다
  const [imageChange, setImageChange] = useState(null);
  const submitting = useRef(false);
  const isBusy = isSaving || isLocating;
  let saveLabel = '변경사항 저장';
  if (isSaving) saveLabel = '저장 중…';
  if (isLocating) saveLabel = '주소 확인·저장 중…';
  const change = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
    setMessage('');
  };
  const submit = async (event) => {
    event.preventDefault();
    if (submitting.current || isSaving) return;
    submitting.current = true;
    setError('');
    setMessage('');
    try {
      let locatedForm = form;
      if (
        form.address.trim() !== shop.address?.trim() ||
        !form.regionCode ||
        form.latitude == null ||
        form.longitude == null
      ) {
        if (!canGeocode())
          throw new Error('주소 자동 확인을 사용할 수 없습니다. 카카오맵 설정을 확인해 주세요.');
        setIsLocating(true);
        const location = await geocodeAddress(form.address);
        locatedForm = { ...form, ...location };
        setForm(locatedForm);
      }
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
      } = locatedForm;
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
      if (imageChange) {
        try {
          await onImage(imageChange.file ?? null);
        } catch (imageFailure) {
          // 매장 정보는 저장됐으므로 이미지 변경만 남겨 두고 다시 저장할 수 있게 한다
          throw new Error(
            `매장 정보는 저장됐지만 이미지 ${imageChange.remove ? '삭제' : '등록'}에 실패했습니다. 다시 저장해 주세요. ${imageFailure.message}`,
          );
        }
        setImageChange(null);
      }
      setMessage('매장 정보를 저장했습니다.');
    } catch (failure) {
      setError(failure.message);
    } finally {
      setIsLocating(false);
      submitting.current = false;
    }
  };
  return (
    <div className="b-stack">
      <h1>매장 관리</h1>
      <Panel padded>
        <h2>매장 대표 이미지</h2>
        <p>
          사진을 바꾸거나 삭제한 뒤 아래 <strong>변경사항 저장</strong>을 누르면 매장 정보와 함께
          반영됩니다.
        </p>
        <OwnerImageEditor
          imageUrl={shop.imageUrl}
          kind="shop"
          change={imageChange}
          disabled={isBusy}
          onChange={(next) => {
            setImageChange(next);
            setMessage('');
          }}
        />
      </Panel>
      <Panel padded>
        <form onSubmit={submit} className="b-stack">
          <fieldset disabled={isBusy} className="b-form-grid">
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
                  [4, '피부 관리'],
                  [5, '메이크업'],
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
            <p className="b-span-two">
              주소를 변경하고 저장하면 위치 정보를 자동으로 확인합니다. 상세 주소는 별도로 입력해
              주세요.
            </p>
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
              disabled={isBusy}
              onClick={() => {
                setForm(shop);
                setImageChange(null);
                setError('');
                setMessage('변경사항을 취소했습니다.');
              }}
            >
              취소
            </Button>
            <Button type="submit" variant="primary" disabled={isBusy}>
              {saveLabel}
            </Button>
          </div>
        </form>
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
  // 입력 폼은 받아 둔 옛 값으로 열지 않는다. 입력하다 새 값에 덮이거나 옛 값으로 저장될 수 있다
  if (!shop || status === 'loading')
    return <EmptyState role="status">매장 정보를 불러오는 중입니다.</EmptyState>;
  return (
    <ShopForm key={shop.shopId} shop={shop} isSaving={isSaving} onSave={save} onImage={saveImage} />
  );
}
