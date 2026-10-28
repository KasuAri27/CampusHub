<template>
  <div class="profile-page">
    <div class="profile-header-banner">
      <el-upload
        class="avatar-uploader"
        action="/v1/files/upload"
        :headers="uploadHeaders"
        :show-file-list="false"
        :before-upload="beforeAvatarUpload"
        :on-success="handleAvatarUploadSuccess"
        :on-error="handleAvatarUploadError"
      >
        <div class="avatar-large avatar-upload-trigger">
          <img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="avatar" />
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
            <circle cx="12" cy="7" r="4" />
          </svg>
          <div class="avatar-upload-mask">
            <el-icon><Plus /></el-icon>
            <span>更换头像</span>
          </div>
        </div>
      </el-upload>

      <div class="user-title-info">
        <h2>{{ profileForm.nickname || '未设置昵称' }}</h2>
        <el-tag :type="creditScoreType" effect="dark" class="score-tag">
          信用分：{{ profileForm.creditScore ?? userStore.creditScore }}
        </el-tag>
      </div>
    </div>

    <el-card class="profile-card">
      <el-tabs v-model="activeTab" class="profile-tabs">
        <el-tab-pane label="基本信息" name="info">
          <el-form :model="profileForm" label-width="90px" class="form-container">
            <div class="form-section-title">账号标识</div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="学号">
                  <el-input v-model="profileForm.studentId" disabled placeholder="暂无学号信息" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="当前角色">
                  <el-input :value="roleName" disabled />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="手机号">
                  <el-input v-model="profileForm.phone" disabled placeholder="未绑定" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="邮箱">
                  <el-input v-model="profileForm.email" disabled placeholder="未绑定" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider border-style="dashed" />

            <div class="form-section-title">个性化设置</div>
            <el-form-item label="用户昵称">
              <el-input v-model="profileForm.nickname" placeholder="请输入新昵称" clearable />
            </el-form-item>

            <el-form-item label="头像上传">
              <div class="avatar-upload-panel">
                <el-upload
                  class="avatar-file-uploader"
                  action="/v1/files/upload"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  :before-upload="beforeAvatarUpload"
                  :on-success="handleAvatarUploadSuccess"
                  :on-error="handleAvatarUploadError"
                >
                  <el-button type="primary">选择本地图片</el-button>
                </el-upload>
                <div class="avatar-upload-note">
                  上传后会自动更新头像预览，并同步保存到个人资料里。
                </div>
              </div>
            </el-form-item>

            <el-form-item class="submit-item">
              <el-button type="primary" size="large" class="save-btn" :loading="loading" @click="updateProfile">
                保存资料修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="安全设置" name="security">
          <el-form :model="passwordForm" label-width="90px" class="form-container password-form">
            <el-alert
              title="密码修改后您可能需要重新登录"
              type="warning"
              show-icon
              :closable="false"
              class="mb-20"
            />
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>

            <el-form-item class="submit-item">
              <el-button type="danger" size="large" class="save-btn" :loading="pwdLoading" @click="updatePassword">
                确认修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/user/userStore'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '@/common/http'

const API_ORIGIN = 'http://localhost:8080'

const userStore = useUserStore()
const activeTab = ref('info')
const loading = ref(false)
const pwdLoading = ref(false)

const profileForm = reactive({
  studentId: '',
  phone: '',
  email: '',
  nickname: '',
  avatar: '',
  creditScore: 100
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const uploadHeaders = computed(() => ({
  Authorization: userStore.token ? `Bearer ${userStore.token}` : '',
  'X-User-Id': userStore.userId || ''
}))

const roleName = computed(() => {
  if (userStore.isAdmin) return '管理员'
  if (userStore.isRequester) return '需求方'
  return '服务方'
})

const creditScoreType = computed(() => {
  const score = profileForm.creditScore || userStore.creditScore
  if (score >= 90) return 'success'
  if (score >= 70) return 'warning'
  return 'danger'
})

const avatarPreviewUrl = computed(() => normalizeAvatarUrl(profileForm.avatar) || userStore.avatar || '')

function normalizeAvatarUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return `${API_ORIGIN}${url}`
}

function fillProfileForm(data) {
  profileForm.studentId = data?.studentId || ''
  profileForm.phone = data?.phone || ''
  profileForm.email = data?.email || ''
  profileForm.nickname = data?.nickname || ''
  profileForm.avatar = normalizeAvatarUrl(data?.avatar)
  profileForm.creditScore = data?.creditScore ?? 100
}

onMounted(async () => {
  try {
    const res = await http.get('/auth/profile')
    if (res.data.code === 200) {
      fillProfileForm(res.data.data)
      userStore.syncProfile({
        nickname: profileForm.nickname,
        avatar: profileForm.avatar,
        creditScore: profileForm.creditScore
      })
      return
    }
  } catch (err) {
    // fallback to local store state
  }

  fillProfileForm({
    nickname: userStore.nickname,
    avatar: userStore.avatar,
    creditScore: userStore.creditScore
  })
})

const updateProfile = async () => {
  if (!profileForm.nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }

  loading.value = true
  try {
    const res = await http.put('/auth/profile', {
      nickname: profileForm.nickname,
      avatar: profileForm.avatar || null
    })

    if (res.data.code === 200) {
      userStore.syncProfile({
        nickname: profileForm.nickname,
        avatar: profileForm.avatar,
        creditScore: profileForm.creditScore
      })
      ElMessage.success('资料已更新')
    } else {
      ElMessage.error(res.data.message || '更新失败')
    }
  } catch (err) {
    ElMessage.error('更新失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleAvatarUploadSuccess(res) {
  if (res?.data?.url) {
    profileForm.avatar = normalizeAvatarUrl(res.data.url)
    userStore.syncProfile({
      nickname: profileForm.nickname,
      avatar: profileForm.avatar,
      creditScore: profileForm.creditScore
    })
    ElMessage.success('头像上传成功')
    return
  }
  ElMessage.error('头像解析失败，请重试')
}

function handleAvatarUploadError() {
  ElMessage.error('图片上传失败')
}

function beforeAvatarUpload(file) {
  const allowTypes = ['image/jpeg', 'image/png', 'image/jpg', 'image/webp']
  const isAllowedType = allowTypes.includes(file.type)
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isAllowedType) {
    ElMessage.error('头像图片只能是 JPG / PNG / WebP 格式')
  }
  if (!isLt2M) {
    ElMessage.error('头像图片大小不能超过 2MB')
  }
  return isAllowedType && isLt2M
}

const updatePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('密码不能为空')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  pwdLoading.value = true
  try {
    const res = await http.put('/auth/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    if (res.data.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      userStore.logout()
      location.reload()
    } else {
      ElMessage.error(res.data.message || '密码修改失败')
    }
  } catch (err) {
    ElMessage.error('密码修改失败，请稍后重试')
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
  animation: fade-in 0.3s ease;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.profile-header-banner {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 36px 40px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1), rgba(59, 130, 246, 0.06));
  border-radius: var(--radius-xl);
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
}

.avatar-uploader {
  flex-shrink: 0;
}

.avatar-large {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: white;
  border: 4px solid white;
  box-shadow: var(--shadow-md);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: var(--text-muted);
  position: relative;
}

.avatar-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-large svg {
  width: 52px;
  height: 52px;
}

.avatar-upload-trigger {
  cursor: pointer;
}

.avatar-upload-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  opacity: 0;
  transition: opacity var(--transition-base);
  font-size: 13px;
  font-weight: 600;
}

.avatar-upload-trigger:hover .avatar-upload-mask {
  opacity: 1;
}

.user-title-info h2 {
  margin: 0 0 12px;
  font-size: 28px;
  color: var(--text-primary);
  font-weight: 700;
}

.score-tag {
  font-size: 14px;
  padding: 4px 12px;
  border-radius: var(--radius-full);
}

.profile-card {
  border-radius: var(--radius-xl);
  padding: 10px;
}

.form-container {
  padding: 20px 0;
  max-width: 640px;
}

.password-form {
  max-width: 520px;
}

.form-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 20px;
  margin-top: 10px;
  border-left: 3px solid var(--primary);
  padding-left: 10px;
}

.avatar-upload-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.avatar-upload-note {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.submit-item {
  margin-top: 40px;
}

.save-btn {
  width: 150px;
  border-radius: var(--radius-full);
}

.mb-20 {
  margin-bottom: 20px;
}

:deep(.el-upload) {
  display: inline-flex;
}
</style>
