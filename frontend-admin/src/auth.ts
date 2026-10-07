import axios from 'axios'
import { ref } from 'vue'

/** 登录身份来自服务端；密码和 JWT 不写入浏览器存储。 */
export interface Identity {
  id: string
  name: string
  role: string
  companyId: number | ''
  canManageTeachers: boolean
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

/** 账号密码仅在本次请求中发送；管理端只接受老师和平台管理员。 */
export async function login(username: string, password: string) {
  await client.post('/login', { username, password }, { headers: await csrfHeaders() })
  await loadIdentity()
  if (!(
    identity.value?.role === 'admin' ||
    (identity.value?.role === 'teacher' && identity.value.companyId)
  )) {
    await logout()
    throw new Error('请使用老师或平台管理员账号登录教学管理中心')
  }
}

/** 清除服务端 Cookie 后清空页面身份。 */
export async function logout() {
  await client.post('/logout', null, { headers: await csrfHeaders() })
  identity.value = null
}

/** 平台管理员查询老师账号，固定每页十条。 */
export async function getTeachers(pageNo: number) {
  return (await client.get('/teachers', { params: { pageNo } })).data
}

/** 平台创建老师不允许指定角色，教学空间由服务端建立，复用 CSRF 与认证 Cookie。 */
export async function createTeacher(input: {
  username: string
  password: string
  confirmPassword: string
  name: string
}) {
  await client.post('/teachers', input, { headers: await csrfHeaders() })
}
