export const categories = {
  BOOKS: '教材书籍',
  DORM: '宿舍生活',
  ELECTRONICS: '数码配件',
  SPORTS: '运动用品',
  OTHER: '其他闲置',
}

export const conditions = { NEW: '全新', LIKE_NEW: '近新', GOOD: '良好', FAIR: '有使用痕迹' }

export const statuses = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', REJECTED: '驳回', ON_SALE: '在售',
  AUCTIONING: '竞拍中', RESERVED: '已预约', SOLD: '已售', OFF_SHELF: '已下架',
  UNSOLD: '流拍', BLOCKED: '已限制', ACTIVE: '待面交', COMPLETED: '已完成',
  CANCELLED: '已取消', CLOSED: '已关闭',
}

const money = new Intl.NumberFormat('zh-CN', {
  style: 'currency', currency: 'CNY', maximumFractionDigits: 2, minimumFractionDigits: 0,
})

export const price = (amount) => amount == null ? '—' : money.format(amount)
export const localTime = (value) => value ? new Date(value + 'Z').toLocaleString('zh-CN') : '—'
