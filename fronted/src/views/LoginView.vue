<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../session'

const router = useRouter()
const phone = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await login({ phone: phone.value, password: password.value })
    router.replace('/')
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="shell narrow page">
    <h1>登录</h1>
    <form class="stack panel" @submit.prevent="submit">
      <label>手机号 <input v-model.trim="phone" type="tel" autocomplete="tel" required /></label>
      <label>密码 <input v-model="password" type="password" autocomplete="current-password" required /></label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="primary" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button>
      <RouterLink to="/register">注册新账号</RouterLink>
    </form>
  </main>
</template>
