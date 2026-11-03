import { useUserStore } from '@/user/userStore'
import { ElMessage } from 'element-plus'
import { createHttpClient } from '@/common/httpClient'

// 每次请求读取当前JWT，登出与切换账号后不会复用旧身份。
export default createHttpClient({
  getToken: () => useUserStore().token,
  onError: message => ElMessage.error(message)
})
