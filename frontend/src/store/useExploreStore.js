import { create } from 'zustand';
import { exploreApi } from '@/api/exploreApi';
import { createExploreStore } from './createExploreStore';

export const useExploreStore = createExploreStore(create, exploreApi);
