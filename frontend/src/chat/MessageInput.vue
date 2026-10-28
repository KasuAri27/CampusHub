<template>
  <div class="msg-input-bar">
    <input type="file" ref="fileInput" accept="image/*" style="display: none" @change="uploadImage" />

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
import { useUserStore } from '@/user/userStore'

const emit = defineEmits(['send'])
const text = ref('')
const fileInput = ref(null)
const userStore = useUserStore()

function sendText() {
  if (!text.value.trim()) return
  emit('send', { content: text.value, msgType: 'TEXT' })
  text.value = ''
}

function triggerUpload() {
  fileInput.value.click()
}

async function uploadImage(e) {
  const file = e.target.files[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  try {
    // 复用已有的文件上传接口
    const res = await fetch('/v1/files/upload', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${userStore.token}`
      },
      body: formData
    })
    const json = await res.json()
    if (json.code === 200 && json.data?.url) {
      emit('send', { content: json.data.url, msgType: 'IMAGE' })
    } else {
      ElMessage.error(json.message || '图片上传失败')
    }
  } catch (error) {
    ElMessage.error('图片上传失败')
  } finally {
    e.target.value = '' // 清空选择，允许重复上传同一张图片
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
