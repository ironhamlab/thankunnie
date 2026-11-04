import { create } from 'zustand';

// 헤더 검색창 입력값. 홈 빈자리 검색이 같은 값을 읽고 필터 초기화 때 비운다
export const useSearchStore = create((set) => ({
  search: '',
  setSearch: (search) => set({ search }),
}));
