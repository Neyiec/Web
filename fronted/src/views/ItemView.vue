<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { post, request } from '../api/request'
import { account } from '../session'
import { categories, conditions, localTime, price, statuses } from '../market'

const route = useRoute()
const router = useRouter()
const item = ref(null)
const quote = ref(null)
const amount = ref('')
const message = ref('')
const error = ref('')
const busy = ref(false)
const biddingClosed = ref(false)

async function load() {
  const id = route.params.id
  try {
    const result = await request('/api/items/' + id)
    if (route.params.id === id) {
      item.value = result
      quote.value = null
      biddingClosed.value = result.status === 'AUCTIONING' && Date.parse(result.endsAt + 'Z') <= Date.now()
    }
  } catch (failure) {
    if (route.params.id === id) {
      if (failure.status === 404) item.value = null
      error.value = failure.message
    }
  }
}

async function refreshQuote() {
  if (item.value?.status !== 'AUCTIONING') return
  const id = item.value.id
  try {
    const current = await request('/api/items/' + id + '/quote')
    if (item.value?.id === id) quote.value = current
  } catch (failure) {
    if (failure.status === 404 && item.value?.id === id) {
      biddingClosed.value = true
      await load()
    }
  }
}

async function perform(action) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

function chat() {
  return perform(async () => {
    const result = await post('/api/conversations', { itemId: item.value.id })
    router.push({ path: '/messages', query: { id: result.id } })
  })
}

function reserve() {
  return perform(async () => {
    await post('/api/reservations', { itemId: item.value.id, requestId: crypto.randomUUID() })
    router.push('/reservations')
  })
}

function bid() {
  return perform(async () => {
    await post('/api/auctions/' + item.value.id + '/bids', {
      auctionId: item.value.auctionId, requestId: crypto.randomUUID(), amount: amount.value,
    })
    amount.value = ''
    await load()
    message.value = '出价成功。'
  })
}

watch(() => route.params.id, () => {
  item.value = null
  error.value = ''
  message.value = ''
  load()
}, { immediate: true })
let timer
function onMarketUpdate(event) {
  if (event.detail.type === 'auction-settled' && event.detail.auctionId === item.value?.auctionId) load()
}
onMounted(() => {
  timer = setInterval(refreshQuote, 4000)
  window.addEventListener('market-update', onMarketUpdate)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('market-update', onMarketUpdate)
})
</script>

<template>
  <main class="shell page">
    <RouterLink to="/">返回集市</RouterLink>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="!item && !error" role="status">正在加载商品…</p>
    <article v-if="item" class="panel product-detail">
      <img v-if="item.hasImage" class="product-image" :src="'/api/items/' + item.id + '/image'" :alt="item.title" />
      <section>
        <p class="muted">{{ categories[item.category] }} · {{ item.campus }} · {{ conditions[item.conditionCode] }}</p>
        <h1>{{ item.title }}</h1>
        <p class="description">{{ item.description }}</p>
        <p>状态：{{ statuses[item.status] }}</p>
        <p v-if="item.saleType === 'FIXED_PRICE'" class="strong-price">{{ price(item.price) }}</p>
        <template v-else-if="item.status === 'AUCTIONING'">
          <p class="strong-price">{{ quote?.highestPrice == null && item.highestPrice == null ? '起拍价' : '当前价' }} {{ price(quote?.highestPrice ?? item.highestPrice ?? item.startPrice) }}</p>
          <p>结束时间 {{ localTime(item.endsAt) }} · 每次至少加 {{ price(item.bidIncrement) }}</p>
          <p v-if="biddingClosed" role="status">出价已截止，等待结算。</p>
        </template>
        <p v-if="message" role="status">{{ message }}</p>
        <div v-if="account?.roles.includes('STUDENT') && account.id !== item.sellerId" class="actions">
          <button type="button" :disabled="busy" @click="chat">联系卖家</button>
          <button v-if="item.status === 'ON_SALE'" type="button" class="primary" :disabled="busy" @click="reserve">预约面交</button>
          <form v-if="item.status === 'AUCTIONING' && !biddingClosed" class="actions" @submit.prevent="bid">
            <label>出价（元） <input v-model="amount" type="number" min="0.01" step="0.01" required /></label>
            <button class="primary" :disabled="busy">确认出价</button>
          </form>
        </div>
        <RouterLink v-else-if="!account" to="/login">登录后参与交易</RouterLink>
      </section>
    </article>
  </main>
</template>
