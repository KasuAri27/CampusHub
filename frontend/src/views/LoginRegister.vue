<template>
  <div class="login-register-container">
    <div class="card">
      <div class="main-title">校园互助服务平台</div>
      <el-tabs v-model="activeTab">
        <!-- 密码登录 -->
        <el-tab-pane label="密码登录" name="passwordLogin">
          <el-form :model="loginForm" label-width="80px">
            <el-form-item label="账号">
              <el-input v-model="loginForm.account" placeholder="学号或手机号" autocomplete="username" name="username" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" name="password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handlePasswordLogin" :loading="loginLoading">
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 短信登录 -->
        <el-tab-pane label="短信登录" name="smsLogin">
          <el-form :model="smsForm" label-width="80px">
            <el-form-item label="手机号">
              <el-input v-model="smsForm.phone" placeholder="手机号" />
            </el-form-item>
            <el-form-item label="验证码">
              <el-input v-model="smsForm.code" placeholder="验证码" style="width: 60%">
                <template #append>
                  <el-button :disabled="countdown > 0" @click="sendSmsCode('LOGIN')">
                    {{ countdown > 0 ? `${countdown}秒后重试` : '获取验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSmsLogin" :loading="smsLoading">
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 注册 -->
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="100px">
            <el-form-item label="学号" prop="studentId">
              <el-input v-model="registerForm.studentId" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="registerForm.phone" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="registerForm.password" type="password" show-password />
              <div class="password-tip">
                * 密码长度6~20位，必须包含字母和数字（如 abc123）
              </div>
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="验证码" prop="smsCode">
              <el-input v-model="registerForm.smsCode" style="width: 60%">
                <template #append>
                  <el-button :disabled="regCountdown > 0" @click="sendSmsCode('REGISTER')">
                    {{ regCountdown > 0 ? `${regCountdown}秒后重试` : '获取验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRegister" :loading="registerLoading">
                注册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/user/userStore'
import http from '@/common/http'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

const activeTab = ref('passwordLogin')
const loginLoading = ref(false)
const smsLoading = ref(false)
const registerLoading = ref(false)
const countdown = ref(0)
const regCountdown = ref(0)

// 定时器引用
let countdownTimer = null
let regCountdownTimer = null

// 表单数据
const loginForm = reactive({ account: '', password: '' })
const smsForm = reactive({ phone: '', code: '' })
const registerForm = reactive({
  studentId: '', phone: '', password: '', confirmPassword: '', smsCode: ''
})
const registerFormRef = ref(null)

// 表单校验规则
const validatePassword = (rule, value, callback) => {
  if (!value) callback(new Error('请输入密码'))
  else if (value.length < 6 || value.length > 20) callback(new Error('密码长度需在6~20位之间'))
  else if (!/^(?=.*[A-Za-z])(?=.*\d)/.test(value)) callback(new Error('密码必须同时包含字母和数字'))
  else callback()
}
const validateConfirm = (rule, value, callback) => {
  if (!value) callback(new Error('请再次输入密码'))
  else if (value !== registerForm.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const registerRules = {
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }
  ],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
  smsCode: [{ required: true, len: 6, message: '请输入6位验证码', trigger: 'blur' }]
}

// 发送验证码（注册时传入学号，登录时只传手机号）
const sendSmsCode = async (type) => {
  const phone = type === 'REGISTER' ? registerForm.phone : smsForm.phone
  if (!phone || !/^1[3-9]\d{9}$/.test(phone)) {
    ElMessage.warning('请填写正确的手机号')
    return
  }
  // 如果是注册，从表单获取学号
  const studentId = type === 'REGISTER' ? registerForm.studentId : undefined
  try {
    const res = await http.post('/verification/send', { phone, type, studentId })
    if (res.data.code === 200) {
      const code = res.data.data
      ElMessage.info(`验证码已发送（测试：${code}）`)
      if (type === 'REGISTER') startCountdown('reg')
      else startCountdown('login')
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (err) {
    ElMessage.error('发送失败，请重试')
  }
}

// 修正后的倒计时函数
const startCountdown = (which) => {
  const time = 60
  if (which === 'reg') {
    // 清除已有定时器
    if (regCountdownTimer) clearInterval(regCountdownTimer)
    regCountdown.value = time
    regCountdownTimer = setInterval(() => {
      if (regCountdown.value <= 1) {
        clearInterval(regCountdownTimer)
        regCountdownTimer = null
        regCountdown.value = 0   // 关键：显式置0，按钮恢复可用
      } else {
        regCountdown.value--
      }
    }, 1000)
  } else {
    if (countdownTimer) clearInterval(countdownTimer)
    countdown.value = time
    countdownTimer = setInterval(() => {
      if (countdown.value <= 1) {
        clearInterval(countdownTimer)
        countdownTimer = null
        countdown.value = 0
      } else {
        countdown.value--
      }
    }, 1000)
  }
}

// 密码登录
const handlePasswordLogin = async () => {
  if (!loginForm.account || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loginLoading.value = true
  const success = await userStore.loginWithPassword(loginForm.account, loginForm.password)
  loginLoading.value = false
  if (success) {
    router.push('/')
  }
}

// 短信登录
const handleSmsLogin = async () => {
  if (!smsForm.phone || !smsForm.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  smsLoading.value = true
  const success = await userStore.loginWithSms(smsForm.phone, smsForm.code)
  smsLoading.value = false
  if (success) {
    router.push('/')
  }
}

// 注册
const handleRegister = async () => {
  let valid = false
  await registerFormRef.value.validate((isValid) => { valid = isValid })
  if (!valid) return
  registerLoading.value = true
  const success = await userStore.register(
    registerForm.studentId,
    registerForm.phone,
    registerForm.password,
    registerForm.smsCode
  )
  registerLoading.value = false
  if (success) {
    // 注册成功后切换到登录页
    activeTab.value = 'passwordLogin'
    registerForm.studentId = ''
    registerForm.phone = ''
    registerForm.password = ''
    registerForm.confirmPassword = ''
    registerForm.smsCode = ''
  }
}

// 组件卸载前清除定时器
onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (regCountdownTimer) clearInterval(regCountdownTimer)
})
</script>

<style scoped>
.login-register-container {
  max-width: 500px;
  margin: 0 auto;
  padding: 40px 20px;
}
.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
  padding: 24px;
}
.main-title {
  text-align: center;
  font-size: 28px;
  font-weight: bold;
  color: #10b981;
  margin-bottom: 24px;
}
.password-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>