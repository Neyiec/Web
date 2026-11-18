import { createRouter, createWebHistory } from 'vue-router'
import MarketView from '../views/MarketView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: MarketView }],
})
