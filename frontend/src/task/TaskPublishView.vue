<template>
  <div class="publish page">
    <PageHeader title="发布需求" description="选好类型，填写详情，提交后由管理员审核，通过后出现在需求广场。" />

    <ol class="steps" aria-label="发布步骤">
      <li :class="{ on: !selectedType, done: selectedType }"><span class="num">1</span>选择类型</li>
      <li :class="{ on: selectedType }"><span class="num">2</span>填写详情</li>
    </ol>

    <div v-if="blocked" class="blocked panel" role="alert">
      <h2>暂时不能发布</h2>
      <p>你的信用分为 <span class="num">{{ userStore.creditScore }}</span>，低于发布门槛 70 分。信用分每日零点恢复 1 分，恢复到 70 分后即可发布。</p>
      <el-button @click="$router.push('/my-orders')">查看我的订单</el-button>
    </div>

    <div v-else class="layout">
      <section v-if="!selectedType" class="directory" aria-label="需求类型">
        <button
          v-for="(meta, type) in TASK_TYPE"
          :key="type"
          type="button"
          class="dir-row"
          @click="selectType(type)"
        >
          <span class="dir-plate">{{ meta.short }}</span>
          <span class="dir-body">
            <span class="dir-name">{{ meta.name }}</span>
            <span class="dir-desc">{{ meta.desc }}</span>
          </span>
          <span v-if="draftType === type" class="dir-draft">有未提交的草稿</span>
          <svg class="dir-go" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>
        </button>
      </section>

      <section v-else class="form-card panel">
        <div class="form-head">
          <span class="dir-plate">{{ TASK_TYPE[selectedType].short }}</span>
          <h2>{{ TASK_TYPE[selectedType].name }}</h2>
          <button type="button" class="change" @click="selectedType = null">更换类型</button>
        </div>
        <TaskForm :task-type="selectedType" @submitted="onSubmitted" @cancel="selectedType = null" />
      </section>

      <aside class="notes">
        <h2>发布前请留意</h2>
        <ul>
          <li>标题写清要做什么，详情写清时间、地点和要求，接单的同学才能判断能不能做。</li>
          <li>先履约后付款：对方提交完成后由你验收，验收后 1 小时内付款，逾期会扣信用分。</li>
          <li>截止时间前没人接单的需求会被系统自动取消。</li>
          <li>组队只做无偿招募，人数上限包含你自己。</li>
          <li>表单内容会自动保存为草稿，离开页面后回来可以继续填写。</li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import TaskForm from '@/task/TaskForm.vue'
import PageHeader from '@/common/ui/PageHeader.vue'
import { useUserStore } from '@/user/userStore'
import { TASK_TYPE } from '@/common/labels'

/**
 * 发布需求：先选类型再填表单。信用分低于 70 时直接说明原因，不展示表单（后端同样拒绝）。
 * TaskForm 会把草稿存在 localStorage 的 campusHubTaskDraft，这里据此标出有草稿的类型。
 */
const router = useRouter()
const userStore = useUserStore()
const selectedType = ref(null)

const blocked = computed(() => userStore.creditScore < 70)
const draftType = computed(() => {
  try { return JSON.parse(localStorage.getItem('campusHubTaskDraft') || '{}')._taskType || '' }
  catch { return '' }
})

function selectType(type) {
  selectedType.value = type
  window.scrollTo({ top: 0 })
}

function onSubmitted(taskId) {
  router.push(taskId ? `/tasks/${taskId}` : '/my-tasks')
}
</script>

<style scoped>
.steps {
  display: flex;
  gap: 8px;
  margin: 0 0 20px;
  padding: 0;
  list-style: none;
}
.steps li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px 6px 6px;
  border-radius: var(--r-pill);
  background: #eef2f0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}
.steps .num {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  font-size: var(--fs-xs);
  font-weight: 700;
}
.steps li.on { background: var(--pine); color: #fff; font-weight: 600; }
.steps li.on .num { background: var(--apricot); color: var(--ink); }
.steps li.done .num { background: var(--mint); color: #fff; }

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 28px;
  align-items: start;
}

/* 类型目录：仿楼层索引牌 */
.directory {
  border-top: 3px solid var(--pine);
  background: var(--surface);
  border-radius: 0 0 var(--r-card) var(--r-card);
  border-left: 1px solid var(--line);
  border-right: 1px solid var(--line);
}
.dir-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.dir-row:last-child { border-radius: 0 0 var(--r-card) var(--r-card); }
.dir-row:hover { background: var(--mint-wash); }
.dir-row:hover .dir-go { color: var(--pine); transform: translateX(2px); }
.dir-plate {
  flex-shrink: 0;
  width: 52px;
  padding: 4px 0;
  border-radius: var(--r-sign);
  background: var(--pine);
  color: #fff;
  font-weight: 700;
  text-align: center;
  box-shadow: inset 0 -3px 0 var(--apricot);
}
.dir-body { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.dir-name { font-size: var(--fs-lg); font-weight: 600; color: var(--ink); }
.dir-desc { color: var(--ink-2); font-size: var(--fs-sm); }
.dir-draft {
  padding: 1px 8px;
  border-radius: var(--r-sign);
  background: var(--apricot-wash);
  color: var(--amber-ink);
  font-size: var(--fs-xs);
  white-space: nowrap;
}
.dir-go { width: 18px; height: 18px; color: var(--ink-3); transition: transform var(--transition-fast); }

.form-card { padding: 22px 24px 24px; }
.form-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.form-head h2 { font-size: var(--fs-xl); }
.form-head .dir-plate { width: 44px; font-size: var(--fs-sm); }
.change {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--mint);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
}
.change:hover { text-decoration: underline; }

.notes {
  padding: 18px 18px 18px 20px;
  border-left: 3px solid var(--apricot);
  background: var(--surface);
  border-radius: 0 var(--r-card) var(--r-card) 0;
}
.notes h2 { font-size: var(--fs-md); font-weight: 600; margin-bottom: 8px; }
.notes ul { margin: 0; padding-left: 18px; color: var(--ink-2); font-size: var(--fs-sm); }
.notes li + li { margin-top: 6px; }

.blocked { padding: 24px; max-width: 560px; }
.blocked h2 { font-size: var(--fs-lg); margin-bottom: 6px; }
.blocked p { color: var(--ink-2); margin-bottom: 16px; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
