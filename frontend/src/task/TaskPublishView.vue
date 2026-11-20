<template>
  <div class="publish page">
    <PageHeader
      :title="editId ? '修改需求' : '发布需求'"
      :description="editId ? '修改后重新提交，管理员审核通过后出现在需求广场。需求类型不能更改。' : '选好类型，填写详情，提交后由管理员审核，通过后出现在需求广场。'"
    />

    <div v-if="editId && editError" class="blocked panel" role="alert">
      <h2>无法修改这条需求</h2>
      <p>{{ editError }}</p>
      <el-button @click="$router.push(`/tasks/${editId}`)">返回需求详情</el-button>
    </div>
    <div v-else-if="editId && editTask?.status === 'rejected'" class="reject-note" role="status">
      <strong>管理员的驳回理由</strong>
      <p>{{ editTask.rejectReason || '管理员没有填写具体理由。' }}</p>
    </div>

    <ol v-if="!editId" class="steps" aria-label="发布步骤">
      <li :class="{ on: !selectedType, done: selectedType }"><span class="num">1</span>选择类型</li>
      <li :class="{ on: selectedType }"><span class="num">2</span>填写详情</li>
    </ol>

    <div v-if="blocked && !(editId && editError)" class="blocked panel" role="alert">
      <h2>暂时不能发布</h2>
      <p>你的信用分为 <span class="num">{{ userStore.creditScore }}</span>，低于发布门槛 70 分。信用分每日零点恢复 1 分，恢复到 70 分后即可发布。</p>
      <el-button @click="$router.push('/my-orders')">查看我的订单</el-button>
    </div>

    <div v-else-if="!editId || editTask" v-loading="editLoading" class="layout">
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
          <button v-if="!editId" type="button" class="change" @click="selectedType = null">更换类型</button>
        </div>
        <TaskForm
          :key="editId || selectedType"
          :task-type="selectedType"
          :task-id="editId"
          :initial="editTask"
          @submitted="onSubmitted"
          @cancel="onCancel"
        />
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TaskForm from '@/task/TaskForm.vue'
import PageHeader from '@/common/ui/PageHeader.vue'
import taskApi from '@/task/taskApi'
import { useUserStore } from '@/user/userStore'
import { TASK_TYPE } from '@/common/labels'

/**
 * 发布需求：先选类型再填表单。信用分低于 70 时直接说明原因，不展示表单（后端同样拒绝）。
 * TaskForm 会把草稿存在 localStorage 的 campusHubTaskDraft，这里据此标出有草稿的类型。
 * 路由带 id（/tasks/:id/edit）时为修改并重新提交（API 契约 3.6）：只有发布者本人、
 * 且需求处于待审核或被驳回时可改，类型固定为原需求类型；最终校验以后端为准。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const selectedType = ref(null)

const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const editTask = ref(null)
const editLoading = ref(false)
const editError = ref('')

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

function onCancel() {
  if (editId.value) router.push(`/tasks/${editId.value}`)
  else selectedType.value = null
}

async function loadEditTask() {
  editLoading.value = true
  try {
    const task = (await taskApi.getById(editId.value)).data.data
    if (Number(task.publisher?.userId) !== Number(userStore.userId)) {
      editError.value = '只能修改自己发布的需求。'
    } else if (!['pending_review', 'rejected'].includes(task.status)) {
      editError.value = '只有待审核或被驳回的需求可以修改，这条需求已经进入后续流程。'
    } else {
      editTask.value = task
      selectedType.value = task.taskType
    }
  } catch {
    editError.value = '需求不存在，或你没有查看权限。'
  } finally {
    editLoading.value = false
  }
}

onMounted(() => {
  if (editId.value) loadEditTask()
})
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
.reject-note {
  margin-bottom: 18px;
  padding: 12px 16px;
  border-left: 3px solid var(--danger);
  border-radius: 0 var(--r-card) var(--r-card) 0;
  background: var(--danger-wash);
  color: var(--ink);
}
.reject-note strong { color: var(--danger); font-size: var(--fs-sm); }
.reject-note p { margin-top: 4px; white-space: pre-wrap; }
.blocked h2 { font-size: var(--fs-lg); margin-bottom: 6px; }
.blocked p { color: var(--ink-2); margin-bottom: 16px; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
