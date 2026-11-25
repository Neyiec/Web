import { request } from './request'

export function getCatalog(keyword = '', category = '') {
  const params = new URLSearchParams({ keyword, category })
  return request('/api/items?' + params)
}
