import { readonly, ref } from 'vue'
import { post, request } from './api/request'

const current = ref(null)
export const account = readonly(current)

export async function loadAccount() {
  try {
    current.value = await request('/api/auth/me')
  } catch (failure) {
    if (failure.status === 401) {
      current.value = null
      return
    }
    throw failure
  }
}

export async function login(credentials) {
  current.value = await post('/api/auth/login', credentials)
}

export async function logout() {
  try {
    await post('/api/auth/logout')
  } catch (failure) {
    if (failure.status !== 401) throw failure
  }
  current.value = null
}
