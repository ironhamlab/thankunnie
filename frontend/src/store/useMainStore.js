import { create } from 'zustand';
import { itemApi } from '../api/itemApi.js';
import { createMainStore } from './createMainStore.js';

export const useMainStore = createMainStore(create, itemApi);
