import { useRef, useState } from 'react';
import { useSignupVerificationStore } from '@/store/useSignupVerificationStore';

const inputKey = (form) =>
  JSON.stringify([form.businessNumber, form.representativeName.trim(), form.openedOn]);

export default function useBusinessVerification(form) {
  const verifyBusiness = useSignupVerificationStore((state) => state.verifyBusiness);
  const [verifiedKey, setVerifiedKey] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const version = useRef(0);

  const reset = () => {
    version.current += 1;
    setVerifiedKey('');
    setMessage('');
    setBusy(false);
  };

  const handleVerify = async () => {
    if (
      !/^[0-9]{10}$/.test(form.businessNumber) ||
      !form.representativeName.trim() ||
      !form.openedOn
    ) {
      setMessage('사업자등록번호 10자리·대표자명·개업일자를 모두 입력해 주세요.');
      return;
    }
    const requestVersion = ++version.current;
    const key = inputKey(form);
    setBusy(true);
    setVerifiedKey('');
    setMessage('');
    try {
      const result = await verifyBusiness({
        businessNumber: form.businessNumber,
        representativeName: form.representativeName.trim(),
        openedOn: form.openedOn,
      });
      if (version.current !== requestVersion) return;
      if (result.verified) {
        setVerifiedKey(key);
        setMessage('국세청 사업자 정보가 확인되었습니다.');
      }
    } catch (error) {
      if (version.current === requestVersion) setMessage(error.message);
    } finally {
      if (version.current === requestVersion) setBusy(false);
    }
  };

  return { verified: verifiedKey === inputKey(form), busy, message, reset, handleVerify };
}
