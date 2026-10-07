/** 两个前端共用提示规则；保留业务中文原因，不向用户展示英文协议错误或异常堆栈。 */
export function errorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  const failure = error as {
    message?: unknown
    code?: string
    name?: string
    isAxiosError?: boolean
    config?: { url?: string }
    response?: { status?: number; data?: Record<string, unknown> }
  } | null
  const readable = (value: unknown): value is string =>
    typeof value === 'string' &&
    /[\u3400-\u9fff]/u.test(value) &&
    !/(?:Exception|Traceback|\bat\s+[\w.$]+\(|<\/?(?:html|body)|SQLSTATE)/i.test(value)
  const data = failure?.response?.data
  for (const value of [data?.errMessage, data?.msg, data?.detail, data?.message]) {
    if (readable(value)) return value.trim()
  }
  if (failure?.code === 'ECONNABORTED' || failure?.code === 'ETIMEDOUT')
    return '请求超时，请稍后重试'
  if (failure?.code === 'ERR_CANCELED' || failure?.name === 'AbortError') return '操作已取消'
  const status = failure?.response?.status
  if (status === 401 && failure?.config?.url?.endsWith('/login'))
    return '账号或密码错误，请重新输入'
  const messages: Record<number, string> = {
    400: '填写的信息不正确，请检查后重新提交',
    401: '登录状态已失效，请重新登录',
    403: '你没有执行此操作的权限，请使用具有相应权限的账号',
    404: '内容不存在或已下架，请刷新页面后重试',
    405: '当前操作暂不支持，请刷新页面后重试',
    408: '请求超时，请稍后重试',
    409: '提交的信息与已有数据冲突，请检查账号是否重复或刷新状态后重试',
    413: '上传文件过大，请缩小文件后重试',
    415: '提交的文件或数据格式不支持，请检查后重试',
    422: '填写的信息不符合要求，请检查后重新提交',
    429: '操作过于频繁，请稍后再试',
    500: '服务处理异常，请稍后重试',
    502: '服务暂时无法连接，请稍后重试',
    503: '服务暂不可用，请稍后重试',
    504: '服务响应超时，请稍后重试',
  }
  if (status && messages[status]) return messages[status]
  if (failure?.code === 'ERR_NETWORK' || (failure?.isAxiosError && !failure.response))
    return '无法连接服务，请检查网络连接后重试'
  if (readable(failure?.message)) return failure.message.trim()
  if (readable(error)) return error.trim()
  return fallback
}
