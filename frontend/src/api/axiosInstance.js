import axios from 'axios';
import { DEFAULT_ERROR_MESSAGE, ERROR_MESSAGES } from '@/constants/errorMessages';

const DEFAULT_BASE_URL = 'http://localhost:8080/api';
const REQUEST_TIMEOUT_MS = 10000;

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? DEFAULT_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
});

/**
 * 인증 정보 연동 지점.
 * store 를 직접 import 하면 store → api → axiosInstance → store 로 순환 참조가 생기므로,
 * 앱 시작 시(main.jsx) configureAuth 로 토큰 조회·인증 만료 처리 함수를 주입한다.
 */
let authHandlers = {
  getToken: () => null,
  onUnauthorized: () => {},
};

export function configureAuth(handlers) {
  authHandlers = { ...authHandlers, ...handlers };
}

/**
 * 오류를 { code, message, status } 형태로 정리한다.
 * 메시지 우선순위: 프론트 매핑 → 서버 메시지 → 기본 메시지
 */
function toApiError({ code, serverMessage, status }) {
  return {
    code,
    message: ERROR_MESSAGES[code] ?? serverMessage ?? DEFAULT_ERROR_MESSAGE,
    status,
  };
}

axiosInstance.interceptors.request.use((config) => {
  const token = authHandlers.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

axiosInstance.interceptors.response.use(
  (response) => {
    // 204 No Content 는 본문이 없으므로 공통 응답 포맷을 검사하지 않는다.
    if (response.status === 204) {
      return null;
    }

    const body = response.data;
    if (body?.success === false) {
      return Promise.reject(
        toApiError({
          code: body.error?.code,
          serverMessage: body.error?.message,
          status: response.status,
        }),
      );
    }

    // 백엔드 ApiResponse<T> 에서 업무 데이터(data)만 반환한다.
    return body?.data ?? null;
  },
  (error) => {
    const status = error.response?.status;
    const apiError = error.response?.data?.error;

    // 토큰을 실어 보낸 요청이 401 이면 기존 인증 만료로 본다.
    // 토큰 없이 보낸 요청(로그인 실패 등)과 403 권한 부족은 자동 로그아웃하지 않는다.
    const sentToken = Boolean(error.config?.headers?.Authorization);
    if (status === 401 && sentToken) {
      authHandlers.onUnauthorized();
    }

    return Promise.reject(
      toApiError({
        code: apiError?.code ?? (status ? `HTTP_${status}` : 'NETWORK_ERROR'),
        serverMessage: apiError?.message,
        status,
      }),
    );
  },
);

export default axiosInstance;
