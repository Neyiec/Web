import { post } from './request'

export function registerAccount(fields) {
  return post('/api/auth/register', fields)
}
