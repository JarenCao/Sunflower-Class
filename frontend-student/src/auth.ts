import axios from 'axios'
import { ref } from 'vue'

/** 登录身份来自服务端；密码和 JWT 不写入浏览器存储。 */
export interface Identity {
  id: string
  name: string
  role: string
  companyId: number | ''
}
export const identity = ref<Identity | null>(null)
const client = axios.create({ baseURL: '/api/auth', timeout: 15000 })

/** 每次写操作读取原生 CSRF 令牌，适应登录、退出和跨页面刷新。 */
export async function csrfHeaders(): Promise<Record<string, string>> {
  const { data } = await client.get('/csrf')
  return { [data.headerName]: data.token }
}

/** 刷新页面由 HttpOnly Cookie 恢复真实身份，网络故障不会伪装成登录成功。 */
export async function loadIdentity(): Promise<Identity | null> {
  try {
    identity.value = (await client.get('/me')).data
    return identity.value
  } catch (error) {
    identity.value = null
    if (axios.isAxiosError(error) && error.response?.status === 401) return null
    throw error
  }
}

/** 账号密码仅在本次请求中发送，学员身份完全由服务端确定。 */
export async function login(username: string, password: string) {
  await client.post('/login', { username, password }, { headers: await csrfHeaders() })
  await loadIdentity()
  // 学习入口只接受学员账号，老师和平台管理员不能访问学员个人业务。
  if (identity.value?.role !== 'student') {
    await logout()
    throw new Error('请使用学员账号登录学习中心')
  }
}

/** 清除服务端 Cookie 后清空页面身份。 */
export async function logout() {
  await client.post('/logout', null, { headers: await csrfHeaders() })
  identity.value = null
}

/** 注册仅提交学员开户字段，令牌仍由后续登录写入 HttpOnly Cookie。 */
export async function register(input: {
  username: string
  password: string
  confirmPassword: string
  name: string
}) {
  await client.post('/register', input, { headers: await csrfHeaders() })
}
