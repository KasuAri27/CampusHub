<template>
  <div class="profile page" v-loading="loading">
    <PageHeader title="个人资料" description="昵称、头像和学院可以随时修改；学号、真实姓名与校园邮箱注册后不能更改。" />

    <div class="layout">
      <aside class="card panel">
        <button type="button" class="avatar-btn" :disabled="uploading" @click="fileInput?.click()">
          <img v-if="avatarUrl" :src="avatarUrl" alt="当前头像" />
          <span v-else class="avatar-fallback">{{ (form.nickname || '同').charAt(0) }}</span>
          <span class="avatar-mask">{{ uploading ? '上传中…' : '更换头像' }}</span>
        </button>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" class="hidden-input" @change="onFileChosen" />
        <h2 class="name">{{ form.nickname || '未设置昵称' }}</h2>
        <p class="role">{{ userStore.isAdmin ? '管理员' : '普通用户' }}<template v-if="profile.studentId">，学号 <span class="num">{{ profile.studentId }}</span></template></p>

        <div v-if="!userStore.isAdmin" class="credit">
          <div class="credit-top">
            <span>信用分</span>
            <span class="credit-value num" :class="creditTone">{{ creditScore }}</span>
          </div>
          <!-- 信用条：70 分为发布与接单门槛 -->
          <div class="meter" role="meter" aria-label="信用分" :aria-valuenow="creditScore" aria-valuemin="0" aria-valuemax="100">
            <span class="meter-fill" :class="creditTone" :style="{ width: `${creditScore}%` }"></span>
            <span class="meter-mark" title="70 分：发布与接单门槛"></span>
          </div>
          <p class="credit-note">{{ creditNote }}</p>
        </div>
        <p v-if="profile.restricted" class="restricted">账号受限：只能处理本人订单、付款、申诉和账号安全。</p>
      </aside>

      <section class="panel main">
        <div class="tabs" role="tablist">
          <button type="button" role="tab" :class="{ on: tab === 'info' }" :aria-selected="tab === 'info'" @click="tab = 'info'">基本资料</button>
          <button type="button" role="tab" :class="{ on: tab === 'password' }" :aria-selected="tab === 'password'" @click="tab = 'password'">修改密码</button>
        </div>

        <el-form v-if="tab === 'info'" label-position="top" class="form" @submit.prevent="saveProfile">
          <div class="grid2">
            <el-form-item label="昵称" required>
              <el-input v-model.trim="form.nickname" maxlength="20" show-word-limit />
            </el-form-item>
            <el-form-item label="学院">
              <el-input v-model.trim="form.college" maxlength="50" placeholder="例如：计算机学院" />
            </el-form-item>
          </div>
          <div class="readonly">
            <div><span>学号</span><strong class="num">{{ profile.studentId || '—' }}</strong></div>
            <div><span>手机号</span><strong>{{ profile.phone || '未绑定' }}</strong></div>
            <div><span>校园邮箱</span><strong>{{ profile.email || '未绑定' }}</strong></div>
          </div>
          <el-button type="primary" native-type="submit" :loading="saving">保存资料</el-button>
        </el-form>

        <el-form v-else ref="pwdRef" :model="pwd" :rules="pwdRules" label-position="top" class="form narrow" @submit.prevent="savePassword">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="pwd.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwd.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="pwd.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <p class="pwd-note">修改成功后需要用新密码重新登录。</p>
          <el-button type="primary" native-type="submit" :loading="pwdSaving">修改密码</el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/common/http'
import PageHeader from '@/common/ui/PageHeader.vue'
import { useUserStore } from '@/user/userStore'
import { assetUrl } from '@/common/assetUrl'

/**
 * 个人资料（GET/PUT /v1/auth/profile，PUT /v1/auth/password）。
 * 修改时后端只采纳昵称、头像、学院；头像以上传接口返回的相对路径保存（K6），
 * 上传走统一 http 实例，错误提示与鉴权由拦截器处理（K8）。
 */
const userStore = useUserStore()
const router = useRouter()

const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const pwdSaving = ref(false)
const tab = ref('info')
const fileInput = ref(null)
const pwdRef = ref(null)

const profile = ref({})
const form = reactive({ nickname: '', college: '', avatar: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const avatarUrl = computed(() => assetUrl(form.avatar))
const creditScore = computed(() => Number(profile.value.creditScore ?? userStore.creditScore ?? 100))
const creditTone = computed(() => (creditScore.value < 70 ? 'low' : creditScore.value < 90 ? 'mid' : 'ok'))
const creditNote = computed(() => (creditScore.value < 70
  ? '低于 70 分，暂时不能发布和接单。每日零点恢复 1 分，恢复到 70 分为止。'
  : '不低于 70 分可以发布和接单。五星评价加 1 分，一、二星扣 1 分，付款逾期扣 10 分。'))

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (!value) cb(new Error('请输入新密码'))
      else if (value.length < 6 || value.length > 20) cb(new Error('密码为 6–20 位'))
      else if (!/^(?=.*[A-Za-z])(?=.*\d)/.test(value)) cb(new Error('密码需同时包含字母和数字'))
      else cb()
    }
  }],
  confirmPassword: [{ trigger: 'blur', validator: (rule, value, cb) => (value === pwd.newPassword ? cb() : cb(new Error('两次输入的新密码不一致'))) }]
}

async function load() {
  try {
    const data = (await http.get('/auth/profile')).data.data || {}
    profile.value = data
    form.nickname = data.nickname || ''
    form.college = data.college || ''
    form.avatar = assetUrl(data.avatar)
    userStore.syncProfile({ nickname: data.nickname, avatar: data.avatar, creditScore: data.creditScore, restricted: data.restricted })
  } catch {
    form.nickname = userStore.nickname
    form.avatar = userStore.avatar || ''
  } finally {
    loading.value = false
  }
}

async function onFileChosen(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { ElMessage.warning('头像只能是 JPG、PNG 或 WebP 图片'); return }
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('头像图片不能超过 2MB'); return }
  uploading.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const url = (await http.post('/files/upload', body)).data?.data?.url
    if (!url) throw new Error('上传结果缺少地址')
    form.avatar = assetUrl(url)
    await http.put('/auth/profile', { nickname: form.nickname, avatar: form.avatar, college: form.college })
    userStore.syncProfile({ avatar: form.avatar })
    ElMessage.success('头像已更新')
  } catch (error) {
    if (!error.response) ElMessage.error('头像上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

async function saveProfile() {
  if (!form.nickname) { ElMessage.warning('昵称不能为空'); return }
  saving.value = true
  try {
    // college 传空字符串表示清空（后端约定）
    await http.put('/auth/profile', { nickname: form.nickname, avatar: form.avatar || null, college: form.college })
    userStore.syncProfile({ nickname: form.nickname, avatar: form.avatar })
    ElMessage.success('资料已保存')
  } catch {
    // 拦截器已提示（例如昵称过长）
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  try { await pwdRef.value.validate() } catch { return }
  pwdSaving.value = true
  try {
    await http.put('/auth/password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码已修改，请用新密码登录')
    userStore.logout()
    router.replace('/login')
  } catch {
    // 拦截器已提示（例如当前密码错误）
  } finally {
    pwdSaving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
.card { padding: 24px 22px; text-align: center; }
.avatar-btn {
  position: relative;
  width: 96px;
  height: 96px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  overflow: hidden;
  background: var(--mint-wash);
  cursor: pointer;
}
.avatar-btn img { width: 100%; height: 100%; object-fit: cover; }
.avatar-fallback { font-size: 36px; font-weight: 700; color: var(--pine); }
.avatar-mask {
  position: absolute;
  inset: auto 0 0;
  padding: 4px 0 6px;
  background: rgba(15, 50, 40, 0.65);
  color: #fff;
  font-size: var(--fs-xs);
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.avatar-btn:hover .avatar-mask, .avatar-btn:focus-visible .avatar-mask, .avatar-btn:disabled .avatar-mask { opacity: 1; }
.hidden-input { display: none; }
.name { margin-top: 12px; font-size: var(--fs-xl); }
.role { color: var(--ink-2); font-size: var(--fs-sm); }

.credit {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
  text-align: left;
}
.credit-top { display: flex; align-items: baseline; justify-content: space-between; color: var(--ink-2); font-size: var(--fs-sm); }
.credit-value { font-size: 34px; font-weight: 600; line-height: 1; }
.credit-value.ok { color: var(--mint-hover); }
.credit-value.mid { color: var(--amber-ink); }
.credit-value.low { color: var(--danger); }
.meter {
  position: relative;
  height: 8px;
  margin-top: 10px;
  border-radius: var(--r-pill);
  background: #e7ecea;
}
.meter-fill { position: absolute; inset: 0 auto 0 0; border-radius: var(--r-pill); }
.meter-fill.ok { background: var(--mint); }
.meter-fill.mid { background: var(--apricot); }
.meter-fill.low { background: var(--danger); }
/* 70 分门槛刻度 */
.meter-mark {
  position: absolute;
  left: 70%;
  top: -4px;
  bottom: -4px;
  width: 2px;
  background: var(--pine);
}
.credit-note { margin-top: 10px; color: var(--ink-2); font-size: var(--fs-xs); line-height: 1.6; }
.restricted {
  margin-top: 14px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--apricot-wash);
  color: var(--amber-ink);
  font-size: var(--fs-xs);
  text-align: left;
}

.main { padding: 8px 24px 24px; }
.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--line); margin-bottom: 20px; }
.tabs button {
  position: relative;
  padding: 12px 14px;
  border: 0;
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-weight: 500;
  cursor: pointer;
}
.tabs button.on { color: var(--pine); font-weight: 700; }
.tabs button.on::after {
  content: "";
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 3px;
  background: var(--apricot);
  border-radius: 2px 2px 0 0;
}
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.narrow { max-width: 380px; }
.readonly {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f7f9f8;
}
.readonly div { display: flex; flex-direction: column; min-width: 0; }
.readonly span { color: var(--ink-3); font-size: var(--fs-xs); }
.readonly strong { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pwd-note { margin: -4px 0 16px; color: var(--ink-3); font-size: var(--fs-xs); }

@media (max-width: 860px) {
  .layout { grid-template-columns: 1fr; }
  .grid2, .readonly { grid-template-columns: 1fr; }
}
</style>
