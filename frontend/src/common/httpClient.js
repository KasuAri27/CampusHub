import axios from 'axios'

/** 将网络策略与UI依赖分开，测试可验证真实Axios拦截器，不需要模拟整套页面。 */
export function createHttpClient({ getToken, onError }) {
  const client = axios.create({ baseURL: '/v1', timeout: 10000 })
  client.interceptors.request.use(config => {
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  })
  client.interceptors.response.use(response => {
    // 部分旧接口会以HTTP200携带业务错误码；统一保留失败语义。
    if (typeof response.data?.code === 'number' && response.data.code >= 400) {
      const error = new Error(response.data.message || '操作失败')
      error.response = response
      onError(error.message)
      return Promise.reject(error)
    }
    return response
  }, error => {
    const data = error.response?.data
    const message = !error.response ? '网络连接失败，请稍后重试'
      : error.response.status === 401 ? '请先登录'
        : data?.message || '操作失败，请稍后重试'
    onError(message)
    return Promise.reject(error)
  })
  return client
}
