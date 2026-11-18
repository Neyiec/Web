export async function getCatalog() {
  const response = await fetch('/api/items', { headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new Error('暂时无法读取商品，请稍后重试。')
  }

  return response.json()
}
