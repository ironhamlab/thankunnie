import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ACCOUNT_TYPE } from '@/constants/accountType';
import { ROUTES } from '@/constants/routes';
import { TERMS_TARGET } from '@/constants/terms';
import { useAuthStore } from '@/store/useAuthStore';
import { useTermsStore } from '@/store/useTermsStore';
import {
  collectErrors,
  validateLoginId,
  validateMobilePhone,
  validatePassword,
  validateCoordinate,
  validatePasswordConfirm,
  validateRequired,
} from '@/utils/authValidation';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import AccountFields from './components/AccountFields';
import AuthCard from './components/AuthCard';
import AuthField from './components/AuthField';
import ShopAddressFields from './components/ShopAddressFields';
import SignupVerificationFields from './components/SignupVerificationFields';
import TermsAgreementFields from './components/TermsAgreementFields';
import styles from './ShopSignupPage.module.css';
import useSignupVerification from '@/hooks/useSignupVerification';
import BusinessVerificationFields from './components/BusinessVerificationFields';
import useBusinessVerification from '@/hooks/useBusinessVerification';

const EMPTY_FORM = {
  loginId: '',
  password: '',
  passwordConfirm: '',
  categoryId: '',
  name: '',
  phone: '',
  businessNumber: '',
  representativeName: '',
  openedOn: '',
  address: '',
  addressDetail: '',
  regionCode: '',
  latitude: '',
  longitude: '',
  description: '',
  notice: '',
};

const LOCATION_KEYS = ['regionCode', 'latitude', 'longitude'];

const validateLocation = (form) =>
  form.regionCode.trim() && form.latitude !== '' && form.longitude !== ''
    ? ''
    : '매장 위치(지역코드·좌표)를 확인해 주세요. 주소 확인을 누르면 자동으로 채워져요.';

const toNullable = (value) => value.trim() || null;

// 서버는 좌표를 소수점 7자리까지 받는다. 직접 입력한 값이 더 길면 반올림해서 보낸다
const toCoordinate = (value) => Number(Number(value).toFixed(7));

/**
 * 매장 가입 (REQ-006). 매장 하나당 계정 하나이며, 계정과 매장 필수 정보를 함께 등록한다.
 * 가입하면 바로 매장으로 로그인되어 매장 센터로 이동한다. 대표 이미지는 가입 후 매장 정보에서 올린다.
 * 매장 휴대폰 번호는 인증을 받아야 하며, 고객과 달리 이미 쓰이는 번호여도 가입할 수 있다.
 * 매장 약관은 서버의 매장 약관 목록(target=SHOP)을 보여 주고, 동의한 약관을 가입 요청과 함께 보낸다.
 */
export default function ShopSignupPage() {
  const navigate = useNavigate();
  const status = useAuthStore((state) => state.status);
  const error = useAuthStore((state) => state.error);
  const signup = useAuthStore((state) => state.signup);
  const clearError = useAuthStore((state) => state.clearError);
  const categories = useAuthStore((state) => state.shopCategories);
  const categoriesStatus = useAuthStore((state) => state.shopCategoriesStatus);
  const categoriesError = useAuthStore((state) => state.shopCategoriesError);
  const loadShopCategories = useAuthStore((state) => state.loadShopCategories);
  const [form, setForm] = useState(EMPTY_FORM);
  const businessVerification = useBusinessVerification(form);
  const verification = useSignupVerification(ACCOUNT_TYPE.SHOP, form.loginId, form.phone);
  const [fieldErrors, setFieldErrors] = useState({});
  const terms = useTermsStore((state) => state.terms);
  const termsStatus = useTermsStore((state) => state.status);
  const termsError = useTermsStore((state) => state.error);
  const loadTerms = useTermsStore((state) => state.loadTerms);
  const [agreedTerms, setAgreedTerms] = useState([]);

  useEffect(() => {
    loadShopCategories();
    return clearError;
  }, [loadShopCategories, clearError]);

  useEffect(() => {
    loadTerms(TERMS_TARGET.SHOP);
  }, [loadTerms]);

  const updateForm = (partial) => {
    if (['businessNumber', 'representativeName', 'openedOn'].some((key) => key in partial)) {
      businessVerification.reset();
    }
    if ('loginId' in partial) verification.resetLoginId();
    if ('phone' in partial) verification.resetPhone();
    setForm((prev) => ({ ...prev, ...partial }));
    // 위치(지역코드·좌표) 오류는 주소 칸에 표시하므로 위치가 바뀌면 주소 오류도 함께 지운다
    const changedKeys = Object.keys(partial);
    const clearKeys = changedKeys.some((key) => LOCATION_KEYS.includes(key))
      ? [...changedKeys, 'address']
      : changedKeys;
    setFieldErrors((prev) => ({
      ...prev,
      ...Object.fromEntries(clearKeys.map((key) => [key, ''])),
    }));
  };

  const handleTermsChange = (codes) => {
    setAgreedTerms(codes);
    setFieldErrors((prev) => ({ ...prev, terms: '' }));
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    updateForm({
      [name]: name === 'businessNumber' ? value.replace(/[^0-9]/g, '').slice(0, 10) : value,
    });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const errors = collectErrors({
      businessNumber: businessVerification.verified ? '' : '사업자 정보 확인을 완료해 주세요.',
      loginId:
        validateLoginId(form.loginId) ||
        (!verification.isLoginIdChecked ? '아이디 중복 확인을 해 주세요.' : ''),
      password: validatePassword(form.password),
      passwordConfirm: validatePasswordConfirm(form.password, form.passwordConfirm),
      categoryId: form.categoryId ? '' : '카테고리를 선택해 주세요.',
      name: validateRequired(form.name, '매장명'),
      phone:
        validateMobilePhone(form.phone) ||
        (!verification.isPhoneVerified ? '휴대폰 인증을 완료해 주세요.' : ''),
      address: validateRequired(form.address, '매장 주소') || validateLocation(form),
      latitude: validateCoordinate(form.latitude, { label: '위도', min: -90, max: 90 }),
      longitude: validateCoordinate(form.longitude, { label: '경도', min: -180, max: 180 }),
      terms: terms.some((item) => item.required && !agreedTerms.includes(item.code))
        ? '필수 약관에 모두 동의해 주세요.'
        : '',
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    try {
      await signup(ACCOUNT_TYPE.SHOP, {
        loginId: form.loginId,
        password: form.password,
        categoryId: Number(form.categoryId),
        name: form.name.trim(),
        regionCode: form.regionCode.trim(),
        address: form.address.trim(),
        addressDetail: toNullable(form.addressDetail),
        latitude: toCoordinate(form.latitude),
        longitude: toCoordinate(form.longitude),
        phone: form.phone,
        businessNumber: form.businessNumber,
        representativeName: form.representativeName.trim(),
        openedOn: form.openedOn,
        description: toNullable(form.description),
        notice: toNullable(form.notice),
        agreedTerms,
        phoneVerificationId: verification.phoneVerificationId,
      });
      navigate(ROUTES.OWNER, { replace: true });
    } catch {
      // 오류 문구는 store 의 error 로 화면에 표시한다
    }
  };

  const isSubmitting = status === 'loading';
  const isTermsReady = termsStatus === 'succeeded';

  return (
    <AuthCard
      title="매장 가입"
      description="매장 계정으로 가입하고 빈 시간을 열어 고객을 받아보세요."
      width="wide"
    >
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <section className="b-form-grid" aria-labelledby="shop-signup-account">
          <h2 id="shop-signup-account" className="b-span-two">
            계정 정보
          </h2>
          <AccountFields
            value={form}
            onChange={handleChange}
            errors={fieldErrors}
            loginIdLabel="매장 아이디"
            loginIdClassName="b-span-two"
            verification={verification}
          />
        </section>

        <BusinessVerificationFields
          form={form}
          onChange={handleChange}
          verification={businessVerification}
          error={fieldErrors.businessNumber}
        />

        <section className="b-form-grid" aria-labelledby="shop-signup-shop">
          <h2 id="shop-signup-shop" className="b-span-two">
            매장 정보
          </h2>
          <AuthField
            label="매장명"
            name="name"
            value={form.name}
            onChange={handleChange}
            maxLength={100}
            error={fieldErrors.name}
          />
          <AuthField
            as="select"
            label="카테고리"
            name="categoryId"
            value={form.categoryId}
            onChange={handleChange}
            disabled={categoriesStatus !== 'succeeded'}
            error={fieldErrors.categoryId || categoriesError?.message}
          >
            <option value="">
              {categoriesStatus === 'loading' ? '불러오는 중…' : '카테고리 선택'}
            </option>
            {categories.map((category) => (
              <option key={category.categoryId} value={category.categoryId}>
                {category.name}
              </option>
            ))}
          </AuthField>
          <AuthField
            className="b-span-two"
            label="휴대폰 번호"
            name="phone"
            type="tel"
            value={form.phone}
            onChange={handleChange}
            autoComplete="tel"
            placeholder="010-1234-5678"
            hint="본인 휴대폰으로 인증해 주세요. 매장 연락처로 고객에게 안내됩니다."
            error={verification.isPhoneVerified ? '' : fieldErrors.phone}
          />
          <SignupVerificationFields verification={verification} mode="phone" />
          <ShopAddressFields value={form} onChange={updateForm} errors={fieldErrors} />
          <AuthField
            as="textarea"
            className="b-span-two"
            label="매장 소개 (선택)"
            name="description"
            value={form.description}
            onChange={handleChange}
            rows={3}
            maxLength={2000}
          />
          <AuthField
            as="textarea"
            className="b-span-two"
            label="예약 안내 (선택)"
            name="notice"
            value={form.notice}
            onChange={handleChange}
            rows={2}
            maxLength={2000}
            placeholder="예: 예약 시간 10분 전 도착해 주세요"
          />
        </section>

        {isTermsReady && (
          <TermsAgreementFields
            terms={terms}
            agreed={agreedTerms}
            onChange={handleTermsChange}
            error={fieldErrors.terms}
          />
        )}
        {termsStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {termsError.message}{' '}
            <Button size="small" type="button" onClick={() => loadTerms(TERMS_TARGET.SHOP)}>
              다시 불러오기
            </Button>
          </Alert>
        )}

        {error && (
          <Alert as="p" tone="error" role="alert">
            {error.message}
          </Alert>
        )}
        <Button variant="primary" type="submit" wide disabled={isSubmitting || !isTermsReady}>
          {isSubmitting ? '가입하는 중…' : '매장 가입하기'}
        </Button>
      </form>

      <p className={styles.links}>
        이미 매장 계정이 있으신가요?{' '}
        <Link to={ROUTES.LOGIN} state={{ from: { pathname: ROUTES.OWNER } }}>
          매장 로그인
        </Link>
      </p>
    </AuthCard>
  );
}
