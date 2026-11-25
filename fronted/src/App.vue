<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { account, loadAccount, logout } from './session'

const router = useRouter()
const notice = ref('')
let socket
let retry

function connect() {
  if (!account.value?.roles.includes('STUDENT')) return
  const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const connection = new WebSocket(scheme + '//' + location.host + '/ws')
  socket = connection
  connection.onopen = () => window.dispatchEvent(new Event('market-reconnect'))
  connection.onmessage = (message) => {
    const event = JSON.parse(message.data)
    if (event.type === 'auction-settled') {
      notice.value = event.outcome === 'SETTLED'
        ? '拍卖已成交，可在面交预约中查看。'
        : '拍卖已结束，本场流拍。'
    }
    window.dispatchEvent(new CustomEvent('market-update', { detail: event }))
  }
  connection.onclose = () => {
    if (socket !== connection) return
    retry = setTimeout(async () => {
      try {
        await loadAccount()
        if (socket === connection) connect()
      } catch {
        if (socket === connection) connect()
      }
    }, 3000)
  }
}

function disconnect() {
  clearTimeout(retry)
  const previous = socket
  socket = null
  previous?.close()
}

watch(() => account.value?.id, () => {
  disconnect()
  connect()
})

onMounted(() => loadAccount().catch((error) => { notice.value = error.message }))
onUnmounted(disconnect)

async function signOut() {
  try {
    await logout()
    router.push('/')
  } catch (error) {
    notice.value = error.message
  }
}
</script>

<template>
  <header class="site-header">
    <nav class="shell header-content" aria-label="主导航">
      <RouterLink class="brand" to="/">校园二手平台</RouterLink>
      <div class="nav-links">
        <template v-if="account">
          <template v-if="account.roles.includes('STUDENT')">
            <RouterLink to="/seller">我的发布</RouterLink>
            <RouterLink to="/messages">消息</RouterLink>
            <RouterLink to="/reservations">面交预约</RouterLink>
          </template>
          <RouterLink v-if="account.roles.includes('ADMIN')" to="/reviews">商品审核</RouterLink>
          <span class="muted">{{ account.nickname }}</span>
          <button type="button" @click="signOut">退出</button>
        </template>
        <template v-else>
          <RouterLink to="/login">登录</RouterLink>
          <RouterLink to="/register">注册</RouterLink>
        </template>
      </div>
    </nav>
  </header>
  <div v-if="notice" class="shell notice banner" role="status">
    <span>{{ notice }}</span>
    <button type="button" @click="notice = ''">关闭</button>
  </div>
  <RouterView />
</template>
