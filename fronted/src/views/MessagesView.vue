<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { post, request } from '../api/request'
import { account } from '../session'
import { localTime } from '../market'

const route = useRoute()
const router = useRouter()
const conversations = ref([])
const messages = ref([])
const hasOlder = ref(false)
const text = ref('')
const error = ref('')
const sending = ref(false)
const history = ref(null)
let loadVersion = 0

async function loadList() {
  try {
    conversations.value = await request('/api/conversations')
  } catch (failure) {
    error.value = failure.message
  }
}

async function loadMessages(before) {
  const active = route.query.id
  if (!active || Array.isArray(active)) return
  const version = ++loadVersion
  const path = '/api/conversations/' + active
  try {
    const latest = messages.value.at(-1)?.id
    const params = before ? '?before=' + before : latest ? '?after=' + latest : ''
    const items = await request(path + '/messages' + params)
    if (version !== loadVersion || route.query.id !== active) return
    if (before) {
      messages.value = [...items, ...messages.value]
      hasOlder.value = items.length === 50
      return
    }

    const merged = new Map(messages.value.map((message) => [message.id, message]))
    for (const item of items) merged.set(item.id, item)
    if (!messages.value.length) hasOlder.value = items.length === 50
    messages.value = [...merged.values()].sort((a, b) => a.id - b.id)
    if (items.length) await post(path + '/read', { messageId: items.at(-1).id })
    if (latest && items.length === 50 && version === loadVersion) {
      return loadMessages()
    }
    await nextTick()
    if (history.value) history.value.scrollTop = history.value.scrollHeight
    await loadList()
  } catch (failure) {
    error.value = failure.message
  }
}

async function send() {
  const active = route.query.id
  sending.value = true
  error.value = ''
  try {
    await post('/api/conversations/' + active + '/messages', {
      body: text.value, requestId: crypto.randomUUID(),
    })
    if (route.query.id === active) {
      text.value = ''
      await loadMessages()
    }
  } catch (failure) {
    if (route.query.id === active) error.value = failure.message
  } finally {
    sending.value = false
  }
}

function update(event) {
  if (event.detail.type !== 'chat') return
  if (String(event.detail.conversationId) === route.query.id) loadMessages()
  else loadList()
}

function reconnect() {
  loadList()
  loadMessages()
}

watch(() => route.query.id, () => {
  loadVersion++
  messages.value = []
  text.value = ''
  hasOlder.value = false
  error.value = ''
  loadMessages()
})
onMounted(() => {
  reconnect()
  window.addEventListener('market-update', update)
  window.addEventListener('market-reconnect', reconnect)
})
onUnmounted(() => {
  window.removeEventListener('market-update', update)
  window.removeEventListener('market-reconnect', reconnect)
})
</script>

<template>
  <main class="shell page">
    <div class="section-heading"><h1>消息</h1><button type="button" @click="reconnect">刷新</button></div>
    <p v-if="error" class="error notice" role="alert">{{ error }}</p>
    <div class="chat-layout">
      <aside class="panel stack">
        <p v-if="!conversations.length">暂无会话。</p>
        <button v-for="conversation in conversations" :key="conversation.id" type="button"
                :class="{ primary: String(conversation.id) === route.query.id }"
                @click="router.replace({ query: { id: conversation.id } })">
          {{ conversation.peer }} · {{ conversation.title }}{{ conversation.unread ? ' · 未读' : '' }}
        </button>
      </aside>
      <section v-if="route.query.id" class="panel stack">
        <button v-if="hasOlder" type="button" @click="loadMessages(messages[0]?.id)">加载更早消息</button>
        <ol ref="history" class="plain-list chat-history" aria-label="聊天记录">
          <li v-for="message in messages" :key="message.id" :class="{ 'own-message': message.senderId === account?.id }">
            <p class="muted">{{ message.senderId === account?.id ? '我' : '对方' }} · {{ localTime(message.createdAt) }}</p>
            <p class="description">{{ message.body }}</p>
          </li>
        </ol>
        <form class="actions" @submit.prevent="send">
          <label class="message-input">消息 <input v-model.trim="text" maxlength="2000" required /></label>
          <button class="primary" :disabled="sending || !text">发送</button>
        </form>
      </section>
      <p v-else class="notice">选择一个会话查看消息。</p>
    </div>
  </main>
</template>
