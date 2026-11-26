<template>
  <div class="app-shell">
    <a class="skip-link" href="#main">跳到主要内容</a>

    <header v-if="!bare" class="topbar">
      <div class="topbar-inner">
        <router-link to="/" class="app-brand" aria-label="CampusHub 首页">
          <span class="app-brand-plate" aria-hidden="true">互</span>
          <span class="app-brand-word">CampusHub</span>
          <span class="app-brand-sub">校园互助</span>
        </router-link>

        <nav class="app-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="app-nav-link"
            :class="{ active: isActive(item) }"
          >
            {{ item.label }}
            <span v-if="item.badge" class="app-nav-badge num">{{ item.badge }}</span>
          </router-link>
        </nav>

        <div class="topbar-right">
          <template v-if="userStore.isLoggedIn">
            <span
              v-if="userStore.isUser"
              class="credit-chip"
              :class="creditTone"
              :title="creditHint"
            >
              信用 <strong class="num">{{ userStore.creditScore }}</strong>
            </span>
            <span v-if="userStore.isAdmin" class="role-chip">管理员</span>

            <el-dropdown trigger="click" placement="bottom-end" @command="onCommand">
              <button class="account-btn" type="button" aria-label="账户菜单">
                <span class="app-avatar">
                  <img v-if="userStore.avatar" :src="userStore.avatar" alt="" />
                  <span v-else>{{ initial }}</span>
                </span>
                <span class="account-name">{{ userStore.nickname || '用户' }}</span>
                <svg class="app-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <!-- 窄屏时主导航收进账户菜单 -->
                  <el-dropdown-item
                    v-for="item in navItems"
                    :key="`m-${item.to}`"
                    :command="item.to"
                    class="menu-nav-item"
                  >{{ item.label }}<span v-if="item.badge">（{{ item.badge }}）</span></el-dropdown-item>
                  <el-dropdown-item command="/profile" class="profile-item">个人资料</el-dropdown-item>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <router-link v-else :to="{ path: '/login', query: { redirect: route.fullPath } }" class="login-btn">登录 / 注册</router-link>
        </div>
      </div>
    </header>

    <div v-if="!bare && userStore.isLoggedIn && userStore.restricted" class="restricted-bar" role="status">
      账号受限：你仍可以处理欠款、付款和申诉，暂时不能发布或接取新需求。
      <router-link to="/my-orders">查看我的订单</router-link>
    </div>

    <main id="main" :class="bare ? 'app-main-bare' : 'app-main'">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/user/userStore'
import { useChatStore } from '@/chat/chatStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const bare = computed(() => route.meta.bare === true)
const initial = computed(() => (userStore.nickname || '用').charAt(0))

/**
 * 主导航按身份裁剪：
 * 普通用户可发布、接单并查看订单；管理员只审核不发布，因此不显示发布、我的需求与订单入口；
 * 受限账号只保留订单入口（路由守卫同样会拦截其余页面）。
 */
const navItems = computed(() => {
  const unread = chatStore.unreadTotal > 99 ? '99+' : chatStore.unreadTotal || ''
  if (!userStore.isLoggedIn) return [{ to: '/', label: '需求广场', exact: true }]
  if (userStore.restricted) return [{ to: '/my-orders', label: '我的订单' }]
  const items = [{ to: '/', label: '需求广场', exact: true }]
  if (userStore.isUser) {
    items.push(
      { to: '/tasks/publish', label: '发布需求' },
      { to: '/my-tasks', label: '我的需求' },
      { to: '/my-orders', label: '我的订单' }
    )
  }
  items.push({ to: '/chat', label: '消息', badge: unread })
  if (userStore.isAdmin) items.push({ to: '/admin', label: '管理后台' })
  return items
})

function isActive(item) {
  if (item.exact) return route.path === '/' || (route.path.startsWith('/tasks/') && route.path !== '/tasks/publish')
  return route.path === item.to || route.path.startsWith(`${item.to}/`)
}

/** 信用分 70 是发布与接单门槛（后端 TaskService/OrderService 同样校验）。 */
const creditTone = computed(() => {
  const s = userStore.creditScore
  if (s < 70) return 'is-danger'
  if (s < 90) return 'is-warn'
  return 'is-ok'
})
const creditHint = computed(() => userStore.creditScore < 70
  ? '信用分低于 70，暂时不能发布或接单；每日零点逐步恢复'
  : '信用分不低于 70 才能发布和接单')

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
    return
  }
  router.push(cmd)
}

async function syncChatState() {
  if (!userStore.isLoggedIn || userStore.restricted) {
    chatStore.disconnect()
    return
  }
  await chatStore.fetchConversations()
  chatStore.connectWebSocket()
}

onMounted(syncChatState)
watch(() => userStore.isLoggedIn, loggedIn => {
  if (loggedIn) syncChatState()
  else chatStore.disconnect()
})
</script>

<style>
.app-shell { min-height: 100vh; }

.skip-link {
  position: absolute;
  left: 12px;
  top: -40px;
  z-index: 2000;
  padding: 8px 12px;
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
}
.skip-link:focus { top: 8px; }

/* —— 顶栏 —— */
.topbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}
.topbar-inner {
  max-width: calc(var(--content-w) + 48px);
  height: var(--header-h);
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: stretch;
  gap: 28px;
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--ink);
  flex-shrink: 0;
}
.app-brand-plate {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
  font-weight: 700;
  font-size: 17px;
  box-shadow: inset 0 -3px 0 var(--apricot);
}
.app-brand-word {
  font-family: var(--font-num);
  font-weight: 600;
  font-size: 19px;
  letter-spacing: 0.01em;
}
.app-brand-sub {
  color: var(--ink-3);
  font-size: var(--fs-sm);
  padding-left: 10px;
  border-left: 1px solid var(--line);
}

.app-nav {
  display: flex;
  align-items: stretch;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.app-nav-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  color: var(--ink-2);
  text-decoration: none;
  font-size: var(--fs-md);
  font-weight: 500;
  white-space: nowrap;
}
.app-nav-link:hover { color: var(--ink); }
/* 选中项：松绿文字 + 底部杏黄标示条，借用导视牌的指示色 */
.app-nav-link.active { color: var(--pine); font-weight: 600; }
.app-nav-link.active::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 3px;
  background: var(--apricot);
  border-radius: 2px 2px 0 0;
}
.app-nav-badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: var(--r-pill);
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.credit-chip {
  padding: 2px 10px;
  border-radius: var(--r-pill);
  font-size: var(--fs-sm);
  cursor: default;
}
.credit-chip strong { font-size: 15px; margin-left: 2px; }
.credit-chip.is-ok { background: var(--mint-wash); color: var(--mint-hover); }
.credit-chip.is-warn { background: var(--apricot-wash); color: var(--amber-ink); }
.credit-chip.is-danger { background: var(--danger-wash); color: var(--danger); }
.role-chip {
  padding: 2px 10px;
  border-radius: var(--r-sign);
  background: var(--pine);
  color: #fff;
  font-size: var(--fs-xs);
  font-weight: 600;
}

.account-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border: 1px solid transparent;
  border-radius: var(--r-pill);
  background: none;
  font: inherit;
  color: var(--ink);
  cursor: pointer;
}
.account-btn:hover { border-color: var(--line); }
.app-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--mint-wash);
  color: var(--pine);
  font-weight: 600;
  font-size: var(--fs-sm);
}
.app-avatar img { width: 100%; height: 100%; object-fit: cover; }
.account-name {
  max-width: 8em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-md);
  font-weight: 500;
}
.app-caret { width: 14px; height: 14px; color: var(--ink-3); }

.login-btn {
  padding: 6px 14px;
  border-radius: 6px;
  background: var(--mint);
  color: #fff;
  text-decoration: none;
  font-weight: 500;
}
.login-btn:hover { background: var(--mint-hover); }

/* 宽屏时下拉菜单里的导航项隐藏，窄屏时显示 */
.menu-nav-item { display: none !important; }

.restricted-bar {
  padding: 10px 24px;
  background: var(--apricot-wash);
  border-bottom: 1px solid #f1dfb4;
  color: var(--amber-ink);
  font-size: var(--fs-sm);
  text-align: center;
}
.restricted-bar a { margin-left: 8px; color: var(--amber-ink); font-weight: 600; }

.app-main {
  max-width: calc(var(--content-w) + 48px);
  margin: 0 auto;
  padding: 28px 24px 64px;
}
.app-main-bare { min-height: 100vh; }

/* 只有页面切换这一处过渡，且只做透明度 */
.page-enter-active, .page-leave-active { transition: opacity 140ms ease; }
.page-enter-from, .page-leave-to { opacity: 0; }

@media (max-width: 960px) {
  .app-nav { display: none; }
  .app-brand-sub { display: none; }
  .topbar-inner { justify-content: space-between; padding: 0 16px; }
  .menu-nav-item { display: flex !important; }
  .profile-item { border-top: 1px solid var(--line); margin-top: 6px; }
  .app-main { padding: 20px 16px 48px; }
}
@media (max-width: 480px) {
  .account-name, .credit-chip { display: none; }
}
</style>
