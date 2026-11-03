import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 用户会话与权限状态管理 Store。
 * 维护登录凭证 JWT、当前用户角色（发布者/接单人/管理员）、信用分及限制状态，并支持跨页面持久化。
 */
export const useUserStore = defineStore('user', () => {
  const API_ORIGIN = 'http://localhost:8080'

  const userId = ref(null)
  const nickname = ref('')
  const role = ref('')
  const avatar = ref(null)
  const creditScore = ref(100)
  const token = ref(null)
  const restricted = ref(false)

  /** 统一头像访问地址：相对路径自动拼接后端 API Origin，绝对 URL 保持不变。 */
  function normalizeAvatarUrl(url) {
    if (!url) return null
    if (/^https?:\/\//i.test(url)) return url
    return `${API_ORIGIN}${url}`
  }

  const isRequester = computed(() => role.value === 'requester')
  const isProvider = computed(() => role.value === 'provider')
  const isAdmin = computed(() => role.value === 'admin')
  const isLoggedIn = computed(() => userId.value !== null)

  function persist() {
    const data = { userId: userId.value, nickname: nickname.value, role: role.value, avatar: avatar.value, creditScore: creditScore.value, token: token.value, restricted: restricted.value }
    localStorage.setItem('campusHubSession', JSON.stringify(data))
  }

  function login(user, jwtToken) {
    userId.value = user.id
    nickname.value = user.nickname || user.name
    role.value = user.role
    avatar.value = normalizeAvatarUrl(user.avatar)
    creditScore.value = user.creditScore ?? 100
    token.value = jwtToken
    restricted.value = Boolean(user.restricted)
    persist()
  }

  function syncProfile(profile) {
    if (profile.nickname !== undefined) nickname.value = profile.nickname
    if (profile.avatar !== undefined) avatar.value = normalizeAvatarUrl(profile.avatar)
    if (profile.creditScore !== undefined) creditScore.value = profile.creditScore
    if (profile.restricted !== undefined) restricted.value = profile.restricted
    persist()
  }

  function logout() {
    userId.value = null
    nickname.value = ''
    role.value = ''
    avatar.value = null
    creditScore.value = 100
    token.value = null
    restricted.value = false
    localStorage.removeItem('campusHubSession')
  }

  function restoreSession() {
    try {
      const raw = localStorage.getItem('campusHubSession')
      if (!raw) return false
      const data = JSON.parse(raw)
      if (!data.token || !data.userId) return false
      userId.value = data.userId
      nickname.value = data.nickname || ''
      role.value = data.role || ''
      avatar.value = normalizeAvatarUrl(data.avatar)
      creditScore.value = data.creditScore ?? 100
      token.value = data.token
      restricted.value = Boolean(data.restricted)
      return true
    } catch {
      localStorage.removeItem('campusHubSession')
      return false
    }
  }

  /** 普通用户在需求方/服务方之间切换身份 */
  /** 普通用户在需求方/服务方之间切换身份 */
  // 在 userStore.js 中修改 switchRole 方法
  /**
   * 切换用户身份（需求方 <-> 服务方）：
   * 管理员或受限用户禁止切换；成功调用后端接口后才同步变更客户端角色。
   */
  async function switchRole() {
    if (role.value === 'admin' || restricted.value) return

    // 计算新的角色名称
    const newRole = role.value === 'requester' ? 'provider' : 'requester'

    try {
      const { default: http } = await import('@/common/http')

      // 调用后端接口更新数据库
      // 注意：这里的 role 参数要与后端 User.Role 枚举值匹配 ('requester' 或 'provider')
      await http.put(`/auth/role?role=${newRole}`)

      // 后端更新成功后，再更新前端状态
      role.value = newRole

      const { ElMessage } = await import('element-plus')
      ElMessage.success(`已切换为${newRole === 'requester' ? '需求方' : '服务方'}视图`)
    } catch (error) {
      const { ElMessage } = await import('element-plus')
      ElMessage.error('切换身份失败，请重试')
    }
  }

  async function loginWithPassword(account, password) {
    const { default: http } = await import('@/common/http')
    try {
      const res = await http.post('/auth/login/password', { account, password })
      if (res.data.code === 200) {
        const d = res.data.data
        login({ id: d.userId, nickname: d.nickname, role: d.role || 'requester', avatar: null, creditScore: d.creditScore ?? 100, restricted: d.restricted }, d.token)
        return true
      }
    } catch { /* handled by interceptor */ }
    return false
  }

  async function loginWithSms(phone, code) {
    const { default: http } = await import('@/common/http')
    try {
      const res = await http.post('/auth/login/sms', { phone, code })
      if (res.data.code === 200) {
        const d = res.data.data
        login({ id: d.userId, nickname: d.nickname, role: d.role || 'requester', avatar: null, creditScore: d.creditScore ?? 100, restricted: d.restricted }, d.token)
        return true
      }
    } catch { /* handled by interceptor */ }
    return false
  }

  async function register(studentId, phone, password, smsCode) {
    const { default: http } = await import('@/common/http')
    try {
      const res = await http.post('/auth/register', { studentId, name: studentId, phone, password, smsCode })
      if (res.data.code === 200) return true
    } catch { /* handled by interceptor */ }
    return false
  }

  return { userId, nickname, role, avatar, creditScore, token, restricted,
           isRequester, isProvider, isAdmin, isLoggedIn,
           login, logout, restoreSession, switchRole, syncProfile,
           loginWithPassword, loginWithSms, register }
})
