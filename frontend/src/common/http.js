import axios from 'axios'
import { useUserStore } from '@/user/userStore'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/v1',
  timeout: 10000
})

// 请求拦截器
http.interceptors.request.use(config => {
  const userStore = useUserStore()
  // 本地测试模式：不发真实请求，用 adapter 拦截
  if (userStore.token === 'local-test-token') {
    config.adapter = () => Promise.resolve({
      data: { code: 200, message: 'success', data: null },
      status: 200, statusText: 'OK', headers: {}, config
    })
    return config
  }
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  if (userStore.userId) {
      config.headers['X-User-Id'] = userStore.userId
  }
  return config
})

// 响应拦截器
http.interceptors.response.use(
  res => res,
  err => {
    if (!err.response) return Promise.resolve({ data: { code: 200, data: null } })
    const d = err.response?.data
    if (err.response?.status === 401) { ElMessage.error('请先登录') }
    else if (d?.error) { ElMessage.error(d.message + '：' + d.error) }
    else if (d?.message) { ElMessage.error(d.message) }
    return Promise.reject(err)
  }
)

export default http
