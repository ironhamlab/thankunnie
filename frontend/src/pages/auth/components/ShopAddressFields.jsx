import { useState } from 'react';
import { canGeocode, geocodeAddress } from '@/utils/geocodeAddress';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Notice from '@/components/common/Notice';
import AuthField from './AuthField';
import styles from './ShopAddressFields.module.css';

const EMPTY_LOCATION = { regionCode: '', latitude: '', longitude: '' };

/**
 * 매장 주소 입력. "주소 확인"으로 카카오 주소 검색을 해 표준 주소·좌표·시군구 지역코드를 채운다.
 * 카카오맵 키가 없거나 주소를 찾지 못하면 지역코드·좌표를 직접 입력할 수 있다.
 *
 * @param {object} value { address, addressDetail, regionCode, latitude, longitude }
 * @param {(partial: object) => void} onChange 바뀐 필드만 넘긴다
 * @param {object} errors 필드별 오류 문구
 */
export default function ShopAddressFields({ value, onChange, errors }) {
  const [isManual, setIsManual] = useState(!canGeocode());
  const [checkStatus, setCheckStatus] = useState('idle'); // 'idle' | 'loading' | 'succeeded' | 'failed'
  const [checkError, setCheckError] = useState('');

  const handleAddressChange = (event) => {
    // 확인한 뒤 주소를 고치면 이전 좌표가 남지 않도록 위치를 비운다 (직접 입력 중에는 유지)
    onChange(
      isManual
        ? { address: event.target.value }
        : { address: event.target.value, ...EMPTY_LOCATION },
    );
    setCheckStatus('idle');
    setCheckError('');
  };

  const handleCheck = async () => {
    if (!value.address.trim()) {
      setCheckError('주소를 입력해 주세요.');
      return;
    }
    setCheckStatus('loading');
    setCheckError('');
    try {
      const located = await geocodeAddress(value.address.trim());
      onChange(located);
      setCheckStatus('succeeded');
      if (!located.regionCode) {
        setIsManual(true);
      }
    } catch (error) {
      setCheckStatus('failed');
      setCheckError(error.message);
    }
  };

  const handleFieldChange = (event) => onChange({ [event.target.name]: event.target.value });

  return (
    <div className={`b-span-two ${styles.root}`}>
      <div className={styles.addressRow}>
        <AuthField
          label="매장 주소"
          name="address"
          value={value.address}
          onChange={handleAddressChange}
          placeholder="예: 서울특별시 강남구 테헤란로 1"
          maxLength={300}
          error={errors.address}
        />
        {!isManual && (
          <Button type="button" onClick={handleCheck} disabled={checkStatus === 'loading'}>
            {checkStatus === 'loading' ? '확인 중…' : '주소 확인'}
          </Button>
        )}
      </div>

      {checkStatus === 'succeeded' && value.regionCode && (
        <Notice>
          <small>
            주소를 확인했어요 · 지역코드 {value.regionCode} · 좌표 {value.latitude},{' '}
            {value.longitude}
          </small>
        </Notice>
      )}
      {checkError && (
        <Alert as="p" tone="error" role="alert">
          {checkError}
        </Alert>
      )}
      {!isManual && checkStatus === 'failed' && (
        <button type="button" className={styles.manualToggle} onClick={() => setIsManual(true)}>
          지역코드·좌표 직접 입력하기
        </button>
      )}

      {isManual && (
        <div className={styles.manualGrid}>
          <AuthField
            label="지역코드"
            name="regionCode"
            value={value.regionCode}
            onChange={handleFieldChange}
            hint="시군구 5자리 (예: 강남구 11680)"
            maxLength={20}
            error={errors.regionCode}
          />
          <AuthField
            label="위도"
            name="latitude"
            type="number"
            step="any"
            value={value.latitude}
            onChange={handleFieldChange}
            error={errors.latitude}
          />
          <AuthField
            label="경도"
            name="longitude"
            type="number"
            step="any"
            value={value.longitude}
            onChange={handleFieldChange}
            error={errors.longitude}
          />
        </div>
      )}

      <AuthField
        label="상세 주소 (선택)"
        name="addressDetail"
        value={value.addressDetail}
        onChange={handleFieldChange}
        placeholder="예: 2층"
        maxLength={200}
      />
    </div>
  );
}
