<template>
  <div class="auth">
    <!-- 左侧导视牌：平台能做什么 -->
    <aside class="board" aria-label="平台介绍">
      <router-link to="/" class="board-brand">
        <span class="plate" aria-hidden="true">互</span>
        <span class="word">CampusHub</span>
      </router-link>
      <h1 class="board-title">校园互助</h1>
      <p class="board-lead">鼓楼、仙林、浦口、苏州四个校区的同学，在这里互相帮忙。</p>
      <ul class="directory">
        <li v-for="item in directory" :key="item.short">
          <span class="dir-plate">{{ item.short }}</span>
          <span class="dir-text">{{ item.desc }}</span>
        </li>
      </ul>
      <p class="board-foot">信用分不低于 70 分即可发布和接单。</p>
    </aside>

    <main class="form-side">
      <div class="form-box">
        <div class="switch" role="tablist" aria-label="登录方式">
          <button
            v-for="t in tabs"
            :key="t.key"
            type="button"
            role="tab"
            :aria-selected="activeTab === t.key"
            :class="{ on: activeTab === t.key }"
            @click="activeTab = t.key"
          >{{ t.label }}</button>
        </div>

        <!-- 密码登录 -->
        <el-form v-if="activeTab === 'password'" :model="loginForm" label-position="top" class="form" @submit.prevent="handlePasswordLogin">
          <h2>登录</h2>
          <el-form-item label="学号或手机号">
            <el-input v-model.trim="loginForm.account" size="large" autocomplete="username" name="username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" size="large" type="password" show-password autocomplete="current-password" name="password" />
          </el-form-item>
          <el-button type="primary" size="large" native-type="submit" class="submit" :loading="loading">登录</el-button>
        </el-form>

        <!-- 验证码登录：验证码发到账号绑定的校园邮箱 -->
        <el-form v-else-if="activeTab === 'code'" :model="smsForm" label-position="top" class="form" @submit.prevent="handleSmsLogin">
          <h2>验证码登录</h2>
          <el-form-item label="手机号">
            <el-input v-model.trim="smsForm.phone" size="large" maxlength="11" autocomplete="tel" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model.trim="smsForm.code" size="large" maxlength="6" autocomplete="one-time-code" />
              <el-button size="large" :disabled="countdown.login > 0" @click="sendCode('LOGIN')">
                {{ countdown.login > 0 ? `${countdown.login} 秒后重发` : '获取验证码' }}
              </el-button>
            </div>
            <p class="field-hint">验证码发送到账号绑定的校园邮箱，5 分钟内有效。未注册的手机号请先注册。</p>
          </el-form-item>
          <el-button type="primary" size="large" native-type="submit" class="submit" :loading="loading">登录</el-button>
        </el-form>

        <!-- 注册：字段与 API 契约 2.2 一致 -->
        <el-form v-else :model="reg" :rules="regRules" ref="regRef" label-position="top" class="form" @submit.prevent="handleRegister">
          <h2>注册账号</h2>
          <div class="grid2">
            <el-form-item label="学号" prop="studentId">
              <el-input v-model.trim="reg.studentId" size="large" maxlength="20" />
            </el-form-item>
            <el-form-item label="真实姓名" prop="name">
              <el-input v-model.trim="reg.name" size="large" maxlength="20" autocomplete="name" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model.trim="reg.nickname" size="large" maxlength="20" autocomplete="nickname" />
            </el-form-item>
            <el-form-item label="学院（可选）" prop="college">
              <el-input v-model.trim="reg.college" size="large" maxlength="50" />
            </el-form-item>
          </div>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model.trim="reg.phone" size="large" maxlength="11" autocomplete="tel" />
          </el-form-item>
          <div class="grid2">
            <el-form-item label="密码" prop="password">
              <el-input v-model="reg.password" size="large" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="reg.confirmPassword" size="large" type="password" show-password autocomplete="new-password" />
            </el-form-item>
          </div>
          <p class="field-hint pw">6–20 位，同时包含字母和数字。学号与真实姓名注册后不能修改。</p>
          <el-form-item label="邮箱验证码" prop="code">
            <div class="code-row">
              <el-input v-model.trim="reg.code" size="large" maxlength="6" autocomplete="one-time-code" />
              <el-button size="large" :disabled="countdown.register > 0" @click="sendCode('REGISTER')">
                {{ countdown.register > 0 ? `${countdown.register} 秒后重发` : '获取验证码' }}
              </el-button>
            </div>
            <p class="field-hint">验证码发送到 {{ reg.studentId || '学号' }}@smail.nju.edu.cn。</p>
          </el-form-item>
          <el-button type="primary" size="large" native-type="submit" class="submit" :loading="loading">注册并登录</el-button>
        </el-form>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/common/http'
import { useUserStore } from '@/user/userStore'
import { TASK_TYPE } from '@/common/labels'

/**
 * 登录与注册。
 * 验证码通过校园邮箱发送：注册时发到“学号@smail.nju.edu.cn”，验证码登录时发到账号绑定的邮箱。
 * 登录成功后回到进入登录页之前的页面（路由守卫写入的 redirect 参数）。
 */
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const tabs = [
  { key: 'password', label: '密码登录' },
  { key: 'code', label: '验证码登录' },
  { key: 'register', label: '注册' }
]
const activeTab = ref(route.query.tab === 'register' ? 'register' : 'password')
const loading = ref(false)
const directory = Object.values(TASK_TYPE)

const loginForm = reactive({ account: '', password: '' })
const smsForm = reactive({ phone: '', code: '' })
const reg = reactive({ studentId: '', name: '', nickname: '', college: '', phone: '', password: '', confirmPassword: '', code: '' })
const regRef = ref(null)

const PHONE = /^1[3-9]\d{9}$/
const regRules = {
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }, { min: 4, max: 20, message: '学号为 4–20 位', trigger: 'blur' }],
  name: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: PHONE, message: '手机号格式不正确', trigger: 'blur' }],
  password: [{
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (!value) cb(new Error('请输入密码'))
      else if (value.length < 6 || value.length > 20) cb(new Error('密码为 6–20 位'))
      else if (!/^(?=.*[A-Za-z])(?=.*\d)/.test(value)) cb(new Error('密码需同时包含字母和数字'))
      else cb()
    }
  }],
  confirmPassword: [{
    trigger: 'blur',
    validator: (rule, value, cb) => (value === reg.password ? cb() : cb(new Error('两次输入的密码不一致')))
  }],
  code: [{ required: true, len: 6, message: '请输入 6 位验证码', trigger: 'blur' }]
}

// —— 验证码倒计时 ——
const countdown = reactive({ login: 0, register: 0 })
const timers = {}
function startCountdown(key) {
  clearInterval(timers[key])
  countdown[key] = 60
  timers[key] = setInterval(() => {
    countdown[key] -= 1
    if (countdown[key] <= 0) { clearInterval(timers[key]); countdown[key] = 0 }
  }, 1000)
}
onBeforeUnmount(() => Object.values(timers).forEach(clearInterval))

async function sendCode(type) {
  const phone = type === 'REGISTER' ? reg.phone : smsForm.phone
  if (!PHONE.test(phone || '')) { ElMessage.warning('请先填写正确的手机号'); return }
  if (type === 'REGISTER' && !reg.studentId) { ElMessage.warning('请先填写学号，验证码会发到学号对应的校园邮箱'); return }
  try {
    await http.post('/verification/send', { phone, type, studentId: type === 'REGISTER' ? reg.studentId : undefined })
    ElMessage.success('验证码已发送，请查收校园邮箱')
    startCountdown(type === 'REGISTER' ? 'register' : 'login')
  } catch {
    // 拦截器已提示具体原因（如学号已注册、发送过于频繁）
  }
}

function afterLogin() {
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : ''
  router.replace(redirect || (userStore.isAdmin ? '/admin' : '/'))
}

async function handlePasswordLogin() {
  if (!loginForm.account || !loginForm.password) { ElMessage.warning('请输入学号或手机号和密码'); return }
  loading.value = true
  const ok = await userStore.loginWithPassword(loginForm.account, loginForm.password)
  loading.value = false
  if (ok) afterLogin()
}

async function handleSmsLogin() {
  if (!PHONE.test(smsForm.phone) || !smsForm.code) { ElMessage.warning('请填写手机号和验证码'); return }
  loading.value = true
  const ok = await userStore.loginWithSms(smsForm.phone, smsForm.code)
  loading.value = false
  if (ok) afterLogin()
}

async function handleRegister() {
  try { await regRef.value.validate() } catch { return }
  loading.value = true
  const ok = await userStore.register(reg)
  loading.value = false
  if (ok) {
    ElMessage.success('注册成功，已为你登录')
    afterLogin()
  }
}
</script>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: minmax(320px, 5fr) 7fr;
  min-height: 100vh;
}

/* —— 导视牌 —— */
.board {
  display: flex;
  flex-direction: column;
  padding: 40px 48px;
  background: var(--pine);
  color: #fff;
  box-shadow: inset -6px 0 0 var(--apricot);
}
.board-brand { display: flex; align-items: center; gap: 10px; color: #fff; text-decoration: none; }
.plate {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: #fff;
  color: var(--pine);
  border-radius: var(--r-sign);
  font-weight: 700;
  font-size: 18px;
  box-shadow: inset 0 -3px 0 var(--apricot);
}
.word { font-family: var(--font-num); font-weight: 600; font-size: 20px; }
.board-title {
  margin-top: auto;
  color: #fff;
  font-size: 44px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
.board-lead { margin-top: 10px; color: rgba(255, 255, 255, 0.8); max-width: 26em; }

/* 目录牌：像楼宇导视的楼层索引 */
.directory {
  list-style: none;
  margin: 32px 0 0;
  padding: 0;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}
.directory li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
}
.dir-plate {
  width: 44px;
  padding: 2px 0;
  border-radius: var(--r-sign);
  background: var(--apricot);
  color: var(--ink);
  font-size: var(--fs-sm);
  font-weight: 700;
  text-align: center;
  flex-shrink: 0;
}
.dir-text { color: rgba(255, 255, 255, 0.86); font-size: var(--fs-sm); }
.board-foot { margin-top: 28px; margin-bottom: auto; color: rgba(255, 255, 255, 0.66); font-size: var(--fs-sm); }

/* —— 表单 —— */
.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: var(--paper);
}
.form-box { width: min(440px, 100%); }
.switch {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: #e9efec;
  border-radius: 8px;
}
.switch button {
  flex: 1;
  padding: 8px 0;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-weight: 500;
  cursor: pointer;
}
.switch button.on { background: var(--surface); color: var(--pine); font-weight: 700; box-shadow: 0 1px 2px rgba(15, 50, 40, 0.1); }
.form { margin-top: 28px; }
.form h2 { font-size: var(--fs-2xl); margin-bottom: 20px; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.code-row { display: flex; gap: 8px; width: 100%; }
.code-row .el-input { flex: 1; }
.field-hint { margin-top: 4px; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.5; }
.field-hint.pw { margin: -8px 0 14px; }
.submit { width: 100%; margin-top: 8px; }

@media (max-width: 860px) {
  .auth { grid-template-columns: 1fr; }
  .board { padding: 24px; box-shadow: inset 0 -5px 0 var(--apricot); }
  .board-title { margin-top: 24px; font-size: 32px; }
  .directory, .board-foot { display: none; }
}
</style>
