import { useEffect, useRef, useState } from 'react';
import { useSignupVerificationStore } from '@/store/useSignupVerificationStore';
import { validateLoginId, validateMobilePhone } from '@/utils/authValidation';

export default function useSignupVerification(accountType, loginId, phone) {
  const checkLoginId = useSignupVerificationStore((state) => state.checkLoginId);
  const sendPhoneCode = useSignupVerificationStore((state) => state.sendPhoneCode);
  const verifyPhoneCode = useSignupVerificationStore((state) => state.verifyPhoneCode);
  const [checkedLoginId, setCheckedLoginId] = useState('');
  const [idMessage, setIdMessage] = useState('');
  const [idBusy, setIdBusy] = useState(false);
  const [challenge, setChallenge] = useState(null);
  const [code, setCode] = useState('');
  const [phoneMessage, setPhoneMessage] = useState('');
  const [phoneBusy, setPhoneBusy] = useState(false);
  const [now, setNow] = useState(Date.now);
  const idVersion = useRef(0);
  const phoneVersion = useRef(0);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const resetLoginId = () => {
    idVersion.current += 1;
    setCheckedLoginId('');
    setIdMessage('');
    setIdBusy(false);
  };

  const resetPhone = () => {
    phoneVersion.current += 1;
    setChallenge(null);
    setCode('');
    setPhoneMessage('');
    setPhoneBusy(false);
  };

  const handleCheckLoginId = async () => {
    const error = validateLoginId(loginId);
    if (error) {
      setIdMessage(error);
      return;
    }
    const version = ++idVersion.current;
    setIdBusy(true);
    setCheckedLoginId('');
    try {
      const result = await checkLoginId(loginId, accountType);
      if (version !== idVersion.current) return;
      setCheckedLoginId(result.available ? loginId : '');
      setIdMessage(result.available ? '사용 가능한 아이디입니다.' : '이미 사용 중인 아이디입니다.');
    } catch (error) {
      if (version === idVersion.current) setIdMessage(error.message);
    } finally {
      if (version === idVersion.current) setIdBusy(false);
    }
  };

  const handleSendCode = async () => {
    const error = validateMobilePhone(phone);
    if (error) {
      setPhoneMessage(error);
      return;
    }
    const version = ++phoneVersion.current;
    setPhoneBusy(true);
    setChallenge(null);
    setCode('');
    try {
      const result = await sendPhoneCode(phone);
      if (version !== phoneVersion.current) return;
      setChallenge({ ...result, phone });
      setNow(Date.now());
      setPhoneMessage('인증번호를 발송했습니다. 3분 안에 입력해 주세요.');
    } catch (error) {
      if (version === phoneVersion.current) setPhoneMessage(error.message);
    } finally {
      if (version === phoneVersion.current) setPhoneBusy(false);
    }
  };

  const handleVerifyCode = async () => {
    if (!challenge || !/^[0-9]{6}$/.test(code)) {
      setPhoneMessage('문자로 받은 인증번호 6자리를 입력해 주세요.');
      return;
    }
    const version = ++phoneVersion.current;
    setPhoneBusy(true);
    try {
      const result = await verifyPhoneCode(challenge.verificationId, code);
      if (version !== phoneVersion.current) return;
      setChallenge({ ...result, phone });
      setCode('');
      setPhoneMessage('휴대폰 인증이 완료되었습니다. 10분 안에 가입해 주세요.');
      setNow(Date.now());
    } catch (error) {
      if (version === phoneVersion.current) setPhoneMessage(error.message);
    } finally {
      if (version === phoneVersion.current) setPhoneBusy(false);
    }
  };

  const remaining = challenge
    ? Math.max(0, Math.ceil((Date.parse(challenge.expiresAt) - now) / 1000))
    : 0;
  const resendRemaining = challenge
    ? Math.max(0, Math.ceil((Date.parse(challenge.resendAvailableAt) - now) / 1000))
    : 0;
  const isPhoneVerified = Boolean(
    challenge?.verified && challenge.phone === phone && remaining > 0,
  );

  return {
    isLoginIdChecked: Boolean(loginId && checkedLoginId === loginId),
    isPhoneVerified,
    phoneVerificationId: isPhoneVerified ? challenge.verificationId : '',
    idMessage,
    idBusy,
    phoneMessage,
    phoneBusy,
    code,
    setCode,
    challenge,
    remaining,
    resendRemaining,
    resetLoginId,
    resetPhone,
    handleCheckLoginId,
    handleSendCode,
    handleVerifyCode,
  };
}
