import { createRouter, createWebHistory } from 'vue-router'
import MarketView from '../views/MarketView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: MarketView },
    { path: '/register', component: () => import('../views/RegisterView.vue') },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/items/:id', component: () => import('../views/ItemView.vue') },
    { path: '/seller', component: () => import('../views/SellerView.vue') },
    { path: '/reviews', component: () => import('../views/ReviewView.vue') },
    { path: '/messages', component: () => import('../views/MessagesView.vue') },
    { path: '/reservations', component: () => import('../views/ReservationsView.vue') },
  ],
})
