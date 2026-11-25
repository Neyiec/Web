<script setup>
import { ref } from 'vue'
import { registerAccount } from '../api/account'

const phone = ref('')
const nickname = ref('')
const password = ref('')
const submitting = ref(false)
const registered = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await registerAccount({
      phone: phone.value,
      nickname: nickname.value,
      password: password.value,
    })
    password.value = ''
    registered.value = true
  } catch (failure) {
    error.value = failure.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="shell registration">
    <h1>注册账号</h1>
    <div v-if="registered" role="status">
      <p>注册成功，已分配学生角色。</p>
      <RouterLink to="/login">去登录</RouterLink>
    </div>
    <form v-else class="registration-form" @submit.prevent="submit">
      <label>
        手机号
        <input v-model.trim="phone" type="tel" autocomplete="tel" placeholder="国内手机号或带区号的号码" required />
      </label>
      <label>
        昵称
        <input v-model.trim="nickname" autocomplete="nickname" maxlength="40" required />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="new-password" minlength="8" required />
      </label>
      <p v-if="error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="submitting">{{ submitting ? '正在注册…' : '注册' }}</button>
    </form>
  </main>
</template>

<style scoped>
.registration { max-width: 30rem; padding-block: 2rem; }
.registration-form { display: grid; gap: 1rem; }
.registration-form label { display: grid; gap: 0.45rem; }
.registration-form input, .registration-form button {
  font: inherit;
  padding: 0.7rem;
  border: 1px solid #adc2b0;
  border-radius: 0.5rem;
}
.registration-form button { background: #244d38; color: white; }
.registration-form p { color: #a32424; margin: 0; }
</style>
