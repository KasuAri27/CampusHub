<template>
  <div class="empty-chat">
    <span class="empty-icon">💬</span>
    <p>选择一个会话开始聊天</p>
    <p class="sub">接单后自动创建私聊，组队需求自动建群</p>
  </div>
</template>

<style scoped>
.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}
.empty-icon { font-size: 56px; margin-bottom: 16px; }
.empty-chat p { margin: 0; font-size: 15px; }
.sub { font-size: 13px; margin-top: 8px; }
</style>
