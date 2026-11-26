import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { assetUrl } from '@/common/assetUrl'

/**
 * 用户会话与权限状态管理 Store。
 * 维护登录凭证 JWT、当前用户角色（普通用户/管理员）、信用分及限制状态，并支持跨页面持久化。
 * 普通用户不再区分需求方/服务方：同一账号既能发布也能接单，无需切换身份。
 */
export const useUserStore = defineStore('user', () => {
  const userId = ref(null)
  const nickname = ref('')
  const role = ref('')
  const avatar = ref(null)
  const creditScore = ref(100)
  const token = ref(null)
  const restricted = ref(false)

  /** 统一头像访问地址：上传文件走同源代理的相对路径，旧数据里的本机绝对地址也会转回相对路径（K6）。 */
  function normalizeAvatarUrl(url) {
    return assetUrl(url) || null
  }

  /**
   * 统一角色取值：除管理员外一律视为普通用户 user。
   * 角色合并前登录的浏览器里可能还缓存着 requester/provider，恢复会话时同样按普通用户处理。
   */
  function normalizeRole(value) {
    if (!value) return ''
    return value === 'admin' ? 'admin' : 'user'
  }

  const isUser = computed(() => role.value === 'user')
  const isAdmin = computed(() => role.value === 'admin')
  const isLoggedIn = computed(() => userId.value !== null)

  function persist() {
    const data = { userId: userId.value, nickname: nickname.value, role: role.value, avatar: avatar.value, creditScore: creditScore.value, token: token.value, restricted: restricted.value }
    localStorage.setItem('campusHubSession', JSON.stringify(data))
  }

  function login(user, jwtToken) {
    userId.value = user.id
    nickname.value = user.nickname || user.name
    role.value = normalizeRole(user.role)
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
      role.value = normalizeRole(data.role)
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

  /**
   * 解析登录/注册成功响应（API 契约 2.2、2.3）：{ token, role, user: { id, nickname, avatar, creditScore, restricted } }。
   * 旧实现读取顶层 userId，契约收敛后会得到 undefined，导致所有“是否本人”判断失效。
   */
  function applyLoginResponse(data) {
    const u = data?.user
    if (!data?.token || !u?.id) return false
    login({ id: u.id, nickname: u.nickname, role: data.role || 'user', avatar: u.avatar, creditScore: u.creditScore ?? 100, restricted: u.restricted }, data.token)
    return true
  }

  async function loginWithPassword(account, password) {
    const { default: http } = await import('@/common/http')
    try {
      const res = await http.post('/auth/login/password', { account, password })
      return applyLoginResponse(res.data.data)
    } catch { /* 拦截器已提示错误 */ }
    return false
  }

  async function loginWithSms(phone, code) {
    const { default: http } = await import('@/common/http')
    try {
      const res = await http.post('/auth/login/sms', { phone, code })
      return applyLoginResponse(res.data.data)
    } catch { /* 拦截器已提示错误 */ }
    return false
  }

  /**
   * 注册（API 契约 2.2）：学号、真实姓名、手机号、验证码、密码、昵称必填，学院可选。
   * 注册成功即返回登录凭证，直接进入登录态。
   */
  async function register(form) {
    const { default: http } = await import('@/common/http')
    try {
      const res = await http.post('/auth/register', {
        studentId: form.studentId,
        name: form.name,
        phone: form.phone,
        code: form.code,
        password: form.password,
        nickname: form.nickname,
        college: form.college || undefined
      })
      return applyLoginResponse(res.data.data)
    } catch { /* 拦截器已提示错误 */ }
    return false
  }

  return { userId, nickname, role, avatar, creditScore, token, restricted,
           isUser, isAdmin, isLoggedIn,
           login, logout, restoreSession, syncProfile,
           loginWithPassword, loginWithSms, register }
})
