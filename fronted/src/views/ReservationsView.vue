<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { post, request } from '../api/request'
import { account } from '../session'
import { price, statuses } from '../market'

const reservations = ref([])
const busy = ref(false)
const error = ref('')

async function load() {
  try {
    reservations.value = await request('/api/reservations')
  } catch (failure) {
    error.value = failure.message
  }
}

async function change(id, action) {
  busy.value = true
  error.value = ''
  try {
    await post('/api/reservations/' + id + '/' + action)
    await load()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

function onUpdate(event) {
  if (event.detail.type === 'auction-settled') load()
}
onMounted(() => {
  load()
  window.addEventListener('market-update', onUpdate)
})
onUnmounted(() => window.removeEventListener('market-update', onUpdate))
</script>

<template>
  <main class="shell page">
    <div class="section-heading"><h1>面交预约</h1><button type="button" @click="load">刷新</button></div>
    <p v-if="error" class="error notice" role="alert">{{ error }}</p>
    <p v-else-if="!reservations.length" class="notice">暂无预约。</p>
    <ul class="stack plain-list">
      <li v-for="entry in reservations" :key="entry.id" class="panel">
        <div class="section-heading">
          <h2>{{ entry.title }}</h2><span class="tag">{{ statuses[entry.status] }}</span>
        </div>
        <p>{{ price(entry.agreedPrice) }} · {{ entry.auctionId ? '拍卖成交' : '一口价预约' }}</p>
        <p class="muted">买家{{ entry.buyerConfirmedAt ? '已确认' : '待确认' }} · 卖家{{ entry.sellerConfirmedAt ? '已确认' : '待确认' }}</p>
        <div v-if="entry.status === 'ACTIVE'" class="actions">
          <button type="button" class="primary" :disabled="busy || !!(entry.buyerId === account?.id ? entry.buyerConfirmedAt : entry.sellerConfirmedAt)" @click="change(entry.id, 'confirm')">确认面交</button>
          <button type="button" :disabled="busy" @click="change(entry.id, 'cancel')">取消预约</button>
        </div>
      </li>
    </ul>
  </main>
</template>
