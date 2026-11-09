import { useAuthStore } from '@/store/useAuthStore';

/**
 * 로그인 계정이 바뀌는 순간(로그아웃·다른 계정 로그인·세션 만료) 호출한다.
 * 메뉴를 다시 열 때 쓰려고 받아 둔 내용을 다음 계정이 보지 못하게 스토어마다 여기서 비운다.
 * 화면이 그려지기 전에 비워지므로 이전 매장의 내용이 잠깐이라도 보이지 않는다.
 * 개발용 테스트 계정은 바꿀 때 화면을 다시 불러와 스토어가 처음부터 시작한다.
 *
 * @param {() => void} reset 받아 둔 내용을 비우고, 진행 중인 요청의 응답을 버리게 하는 함수
 */
export const onSessionChange = (reset) =>
  useAuthStore.subscribe((state, previous) => {
    if (state.token !== previous.token) {
      reset();
    }
  });
