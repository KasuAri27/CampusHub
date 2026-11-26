<template>
  <div class="msg-input-bar">
    <input type="file" ref="fileInput" accept="image/jpeg,image/png,image/webp" style="display: none" @change="uploadImage" />

    <button class="icon-btn" @click="triggerUpload" title="发送图片">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:20px;height:20px">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
        <circle cx="8.5" cy="8.5" r="1.5"/>
        <polyline points="21 15 16 10 5 21"/>
      </svg>
    </button>

    <input
        v-model="text"
        class="msg-input"
        placeholder="输入消息..."
        maxlength="500"
        @keydown.enter.prevent="sendText"
    />
    <button class="send-btn" @click="sendText" :disabled="!text.trim()">发送</button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/common/http'

const emit = defineEmits(['send'])
const text = ref('')
const fileInput = ref(null)

function sendText() {
  if (!text.value.trim()) return
  emit('send', { content: text.value, msgType: 'TEXT' })
  text.value = ''
}

function triggerUpload() {
  fileInput.value.click()
}

/**
 * 发送图片：上传走统一 http 实例（K8），鉴权头与错误提示由拦截器处理；
 * 消息内容保存上传接口返回的相对路径，展示时经 assetUrl 归一化。
 */
async function uploadImage(e) {
  const file = e.target.files[0]
  e.target.value = '' // 清空选择，允许重复上传同一张图片
  if (!file) return
  // 与后端上传白名单一致（API 契约 §11）：仅 jpg/jpeg/png/webp，扩展名与类型均需匹配
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || !/\.(jpe?g|png|webp)$/i.test(file.name)) {
    ElMessage.warning('只能发送 jpg、png 或 webp 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片不能超过 5MB'); return }

  const formData = new FormData()
  formData.append('file', file)
  try {
    const url = (await http.post('/files/upload', formData)).data?.data?.url
    if (url) emit('send', { content: url, msgType: 'IMAGE' })
    else ElMessage.error('图片上传失败，请重试')
  } catch {
    // 拦截器已提示
  }
}
</script>

<style scoped>
.msg-input-bar {
  display: flex;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--border-color);
}
.icon-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color var(--transition-fast);
}
.icon-btn:hover { color: var(--primary); }
.msg-input {
  flex: 1;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  padding: 10px 18px;
  font-size: 14px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  outline: none;
  transition: border-color var(--transition-fast);
}
.msg-input:focus { border-color: var(--primary); }
.send-btn {
  padding: 10px 22px;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  border: none;
  border-radius: var(--radius-full);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.send-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(16,185,129,0.3); }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
