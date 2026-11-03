import Button from '@/components/common/Button';
import AuthField from './AuthField';

export default function BusinessVerificationFields({ form, onChange, verification, error }) {
  return (
    <section className="b-form-grid" aria-labelledby="shop-signup-business">
      <h2 id="shop-signup-business" className="b-span-two">
        사업자 정보 확인
      </h2>
      <AuthField
        className="b-span-two"
        label="사업자등록번호"
        name="businessNumber"
        value={form.businessNumber}
        onChange={onChange}
        inputMode="numeric"
        maxLength={12}
        placeholder="숫자 10자리"
        error={verification.verified ? '' : error}
        hint="사업자등록증에 적힌 번호를 입력해 주세요."
      />
      <AuthField
        label="대표자명"
        name="representativeName"
        value={form.representativeName}
        onChange={onChange}
        maxLength={100}
        hint="외국인 대표자는 등록된 영문명을 입력해 주세요."
      />
      <AuthField
        label="개업일자"
        name="openedOn"
        type="date"
        value={form.openedOn}
        onChange={onChange}
        hint="사업자등록증의 개업연월일을 선택해 주세요."
      />
      <div className="b-span-two">
        <Button
          type="button"
          size="small"
          disabled={verification.busy}
          onClick={verification.handleVerify}
        >
          {verification.busy ? '국세청 정보 확인 중…' : '사업자 정보 확인'}
        </Button>
        <p role="status">{verification.message}</p>
        <small>
          영업 중인 사업자만 등록할 수 있어요. 신규 개업 정보는 국세청 반영까지 1~2일 걸릴 수
          있어요.
        </small>
      </div>
    </section>
  );
}
