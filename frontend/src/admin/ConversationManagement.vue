<template>
  <div class="conv-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <button class="back-btn" @click="$router.back()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <div class="header-info">
        <h1 class="page-title">会话管理</h1>
        <p class="page-subtitle">查看平台所有群聊和私聊会话</p>
      </div>
    </div>

    <!-- 会话列表视图 -->
    <template v-if="!viewingConv">
      <!-- 筛选项 -->
      <div class="filter-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="filter-tab"
          :class="{ active: activeTab === tab.value }"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 加载状态 -->
      <div class="loading-state" v-if="convLoading">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 会话列表 -->
      <template v-else-if="conversations.length > 0">
        <div class="conv-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conv-card"
            @click="openConversation(conv)"
          >
            <div class="conv-card-header">
              <div class="conv-title-row">
                <span class="conv-type-badge" :class="conv.convType === 'TEAM_GROUP' ? 'group' : 'private'">
                  {{ conv.convType === 'TEAM_GROUP' ? '群聊' : '私聊' }}
                </span>
                <h3 class="conv-name">
                  {{ conv.groupName || '私聊会话' }}
                </h3>
                <span v-if="conv.isDeleted" class="deleted-badge">[已删除]</span>

                <span class="conv-meta">#{{ conv.id }}</span>
              </div>
              <span class="member-count">{{ conv.memberCount }}人</span>
            </div>

            <div class="conv-members" v-if="conv.memberNames?.length">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="member-icon">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
              </svg>
              <span class="member-names">{{ conv.memberNames.join('、') }}</span>
            </div>

            <div class="conv-last-msg" v-if="conv.lastMessageContent">
              <span class="last-msg-label">最新消息：</span>
              <span class="last-msg-sender">{{ conv.lastMessageSenderName }}</span>
              <span class="last-msg-sep">·</span>
              <span class="last-msg-text">{{ formatMessageContent(conv.lastMessageContent, conv.lastMessageMsgType, conv.lastMessageIsRecalled) }}</span>
              <span class="last-msg-time" v-if="conv.lastMessageTime">{{ formatTime(conv.lastMessageTime) }}</span>
            </div>

            <div class="conv-footer">
              <span class="conv-time" v-if="conv.createdAt">
                创建于 {{ formatDate(conv.createdAt) }}
              </span>
              <svg class="conv-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </div>
          </div>
        </div>

        <div class="pagination-bar" v-if="convTotal > convPageSize">
          <button
            class="page-btn"
            :disabled="convPage <= 1"
            @click="convPage--; fetchConversations()"
          >上一页</button>
          <span class="page-info">{{ convPage }} / {{ Math.ceil(convTotal / convPageSize) }}</span>
          <button
            class="page-btn"
            :disabled="convPage >= Math.ceil(convTotal / convPageSize)"
            @click="convPage++; fetchConversations()"
          >下一页</button>
        </div>
      </template>

      <!-- 空状态 -->
      <div class="empty-state" v-else>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <h3>暂无会话</h3>
        <p>当前没有{{ activeTab === 'TEAM_GROUP' ? '群聊' : activeTab === 'TASK_PRIVATE' ? '私聊' : '' }}会话</p>
      </div>
    </template>

    <!-- 消息详情视图 -->
    <template v-else>
      <div class="msg-view">
        <div class="msg-header">
          <button class="back-link" @click="closeConversation">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
            返回列表
          </button>
          <div class="msg-header-info">
            <h2>{{ viewingConv.groupName || '私聊会话' }}</h2>
            <span class="conv-type-badge small" :class="viewingConv.convType === 'TEAM_GROUP' ? 'group' : 'private'">
              {{ viewingConv.convType === 'TEAM_GROUP' ? '群聊' : '私聊' }}
            </span>
            <span v-if="viewingConv.isDeleted" class="deleted-badge">[已删除]</span>

            <span class="conv-meta">#{{ viewingConv.id }} · {{ viewingConv.memberCount }}人</span>
          </div>
        </div>

        <div class="msg-loading" v-if="msgLoading">
          <div class="spinner"></div>
          <p>加载消息中...</p>
        </div>

        <template v-else-if="messages.length > 0">
          <div class="msg-list">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="msg-item"
              :class="{ system: msg.msgType === 'SYSTEM' }"
            >
              <div class="msg-avatar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </div>
              <div class="msg-body">
                <div class="msg-meta">
                  <span class="msg-sender">{{ msg.senderName }}</span>
                  <span class="msg-type-tag" v-if="msg.msgType === 'SYSTEM'">系统</span>
                  <span class="msg-type-tag recalled" v-if="msg.isRecalled">已撤回</span>
                  <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
                </div>

                <div class="msg-content" :class="{ 'is-recalled-content': msg.isRecalled }">
                  <template v-if="isImageMsg(msg.content, msg.msgType)">
                    <el-image
                        style="max-width: 200px; border-radius: 6px; margin-top: 4px; vertical-align: top;"
                        :src="msg.content"
                        :preview-src-list="[msg.content]"
                        fit="cover"
                        hide-on-click-modal
                    />
                    <!-- 补上图片后撤回提示 -->
                    <span v-if="msg.isRecalled" class="recalled-text">[已撤回]</span>
                  </template>
                  <template v-else>
                    {{ msg.content }}
                    <!-- 补上文本后撤回提示 -->
                    <span v-if="msg.isRecalled" class="recalled-text">[已撤回]</span>
                  </template>
                </div>
              </div>
            </div>
          </div>

          <div class="pagination-bar" v-if="msgTotal > msgPageSize">
            <button
              class="page-btn"
              :disabled="msgPage <= 1"
              @click="msgPage--; fetchMessages()"
            >上一页</button>
            <span class="page-info">{{ msgPage }} / {{ Math.ceil(msgTotal / msgPageSize) }}</span>
            <button
              class="page-btn"
              :disabled="msgPage >= Math.ceil(msgTotal / msgPageSize)"
              @click="msgPage++; fetchMessages()"
            >下一页</button>
          </div>
        </template>

        <div class="empty-msg" v-else>
          <p>该会话暂无消息</p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import adminApi from '@/admin/adminApi'

const tabs = [
  { label: '全部', value: '' },
  { label: '群聊', value: 'TEAM_GROUP' },
  { label: '私聊', value: 'TASK_PRIVATE' }
]

const activeTab = ref('')
const convLoading = ref(false)
const conversations = ref([])
const convPage = ref(1)
const convPageSize = 20
const convTotal = ref(0)

const viewingConv = ref(null)
const msgLoading = ref(false)
const messages = ref([])
const msgPage = ref(1)
const msgPageSize = 50
const msgTotal = ref(0)

onMounted(() => {
  fetchConversations()
})

function switchTab(value) {
  activeTab.value = value
  convPage.value = 1
  fetchConversations()
}

async function fetchConversations() {
  convLoading.value = true
  try {
    const params = { page: convPage.value, page_size: convPageSize }
    if (activeTab.value) params.convType = activeTab.value
    const res = await adminApi.listConversations(params)
    const data = res.data?.data || {}
    conversations.value = data.items || []
    convTotal.value = data.totalElements || 0
  } finally {
    convLoading.value = false
  }
}

function openConversation(conv) {
  viewingConv.value = conv
  msgPage.value = 1
  fetchMessages()
}

function closeConversation() {
  viewingConv.value = null
  messages.value = []
}

async function fetchMessages() {
  if (!viewingConv.value) return
  msgLoading.value = true
  try {
    const res = await adminApi.getConversationMessages(viewingConv.value.id, {
      page: msgPage.value,
      page_size: msgPageSize
    })
    const data = res.data?.data || {}
    messages.value = data.items || []
    msgTotal.value = data.totalElements || 0
  } finally {
    msgLoading.value = false
  }
}

function formatTime(d) {
  if (!d) return ''
  const date = new Date(d)
  return date.toLocaleString('zh-CN', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric'
  })
}

function isImageMsg(content, msgType) {
  if (msgType === 'IMAGE') return true;
  if (!content) return false;
  // 兜底正则：如果是以 http 且以图片后缀结尾
  return /^(http|\/?api\/uploads|\/?uploads).*\.(jpg|jpeg|png|gif|webp)(\?.*)?$/i.test(content);
}

// 列表页最后一条消息格式化
function formatMessageContent(content, msgType, isRecalled) {
  if (!content) return '暂无消息';

  let text = isImageMsg(content, msgType) ? '[图片]' : content;

  // 如果是撤回的消息，在列表预览中追加后缀
  if (isRecalled) {
    text += ' [已撤回]';
  }

  return text;
}
</script>

<style scoped>
.conv-page {
  animation: fade-in 0.4s ease;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ==================== 页面头部 ==================== */
.page-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 28px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-base);
  white-space: nowrap;
}

.back-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.back-btn svg { width: 16px; height: 16px; }

.header-info { flex: 1; }

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

/* ==================== 筛选 ==================== */
.filter-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.filter-tab {
  padding: 8px 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.filter-tab:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.filter-tab.active {
  background: var(--primary);
  border-color: var(--primary);
  color: white;
}

/* ==================== 会话列表 ==================== */
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.conv-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  cursor: pointer;
  transition: all var(--transition-base);
}

.conv-card:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.conv-card:hover .conv-arrow {
  transform: translateX(4px);
  color: var(--primary);
}

.conv-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.conv-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.conv-type-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.conv-type-badge.group {
  background: rgba(16, 185, 129, 0.1);
  color: var(--primary-dark);
}

.conv-type-badge.private {
  background: rgba(59, 130, 246, 0.1);
  color: var(--info);
}

.conv-type-badge.small {
  font-size: 10px;
  padding: 1px 8px;
}

.conv-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-meta {
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.member-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  flex-shrink: 0;
}

/* 成员行 */
.conv-members {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 8px;
}

.member-icon {
  width: 14px;
  height: 14px;
  color: var(--text-muted);
  flex-shrink: 0;
  margin-top: 2px;
}

.member-names {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* 最后消息 */
.conv-last-msg {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 12px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
  margin-top: 4px;
  font-size: 13px;
}

.last-msg-label { color: var(--text-muted); flex-shrink: 0; }
.last-msg-sender { color: var(--text-primary); font-weight: 500; flex-shrink: 0; }
.last-msg-sep { color: var(--text-muted); }
.last-msg-text { color: var(--text-secondary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.last-msg-time { color: var(--text-muted); font-size: 12px; flex-shrink: 0; margin-left: auto; }

/* 底部时间 + 箭头 */
.conv-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.conv-time { font-size: 12px; color: var(--text-muted); }

.conv-arrow {
  width: 20px; height: 20px;
  color: var(--text-muted);
  transition: all var(--transition-base);
}

/* ==================== 加载和空状态 ==================== */
.loading-state, .msg-loading {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-muted);
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 12px;
}

@keyframes spin { to { transform: rotate(360deg); } }

.loading-state p, .msg-loading p { margin: 0; font-size: 14px; }

.empty-state, .empty-msg {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-secondary);
}

.empty-state svg {
  width: 56px; height: 56px;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.empty-state h3 { margin: 0 0 8px; font-size: 16px; color: var(--text-primary); }
.empty-state p, .empty-msg p { margin: 0; font-size: 14px; color: var(--text-muted); }

/* ==================== 分页 ==================== */
.pagination-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
}

.page-btn {
  padding: 8px 18px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--text-muted);
}

/* ==================== 消息详情视图 ==================== */
.msg-view {
  animation: fade-in 0.35s ease;
}

.msg-header {
  margin-bottom: 24px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  border: none;
  color: var(--primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
  margin-bottom: 12px;
}

.back-link:hover { background: rgba(16, 185, 129, 0.08); }
.back-link svg { width: 16px; height: 16px; }

.msg-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.msg-header-info h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

/* 消息列表 */
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.msg-item {
  display: flex;
  gap: 14px;
  padding: 16px 20px;
  transition: background var(--transition-fast);
}

.msg-item:not(:last-child) {
  border-bottom: 1px solid var(--border-color);
}

.msg-item:hover {
  background: var(--bg-tertiary);
}

.msg-item.system {
  background: rgba(245, 158, 11, 0.03);
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  flex-shrink: 0;
}

.msg-avatar svg { width: 20px; height: 20px; }

.msg-body { flex: 1; min-width: 0; }

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.msg-sender {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.msg-type-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: rgba(245, 158, 11, 0.15);
  color: var(--accent-dark);
  font-weight: 600;
}

.msg-time {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: auto;
}

.msg-content {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.recalled-text {
  color: var(--text-muted);
  font-size: 13px;
  margin-left: 8px;
  white-space: nowrap;
}

.deleted-badge {
  font-size: 12px;
  color: #ef4444; /* 红色醒目提示 */
  background: rgba(239, 68, 68, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 6px;
  flex-shrink: 0;
}

/* 响应式 */
@media (max-width: 600px) {
  .conv-card { padding: 16px; }
  .msg-item { padding: 12px 14px; gap: 10px; }
  .msg-avatar { width: 30px; height: 30px; }
  .msg-avatar svg { width: 16px; height: 16px; }
}
</style>
