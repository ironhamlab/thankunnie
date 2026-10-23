import { create } from 'zustand';
import axiosInstance from '../api/axiosInstance.js';
import { createItemApi } from '../api/itemApi.js';
import { createMainStore } from './createMainStore.js';

export const useMainStore = createMainStore(create, createItemApi(axiosInstance));
