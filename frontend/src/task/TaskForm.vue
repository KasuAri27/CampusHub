<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-position="top"
    class="task-form"
  >
    <!-- 基本信息 -->
    <div class="form-section">
      <h3 class="section-title">基本信息</h3>

      <el-form-item label="需求标题" prop="title">
        <el-input
          v-model="form.title"
          :placeholder="titlePlaceholder"
          maxlength="50"
          show-word-limit
          size="large"
        />
      </el-form-item>

      <el-form-item label="需求详情" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="5"
          :placeholder="descPlaceholder"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="所属校区" prop="campus">
        <el-select
          v-model="form.campus"
          placeholder="请选择校区"
          size="large"
          style="width: 100%"
          clearable
        >
          <el-option v-for="c in CAMPUSES" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>

      <el-form-item label="截止时间" prop="deadline">
        <el-date-picker
          v-model="form.deadline"
          type="datetime"
          placeholder="选择截止时间"
          :disabled-date="beforeToday"
          size="large"
          style="width: 100%"
          format="YYYY年MM月DD日 HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>
    </div>

    <!-- 类型专属字段 -->
    <div class="form-section">
      <h3 class="section-title">{{ typeSectionTitle }}</h3>

      <!-- ── RUNNING 跑腿服务 ── -->
      <template v-if="taskType === 'RUNNING'">
        <div class="form-row">
          <el-form-item label="取货地点" prop="pickupLocation" class="form-col">
            <el-input v-model="form.pickupLocation" placeholder="例如：校门口驿站" maxlength="100" size="large">
              <template #prefix>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
                  <circle cx="12" cy="10" r="3"/>
                </svg>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="送达地点" prop="deliveryLocation" class="form-col">
            <el-input v-model="form.deliveryLocation" placeholder="例如：3号宿舍楼下" maxlength="100" size="large">
              <template #prefix>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
                  <circle cx="12" cy="10" r="3"/>
                </svg>
              </template>
            </el-input>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="取件码 / 备注" prop="pickupCode" class="form-col">
            <el-input v-model="form.pickupCode" placeholder="可选，取件码或快递单号" maxlength="50" size="large" />
          </el-form-item>
          <el-form-item label="跑腿报酬" prop="expressReward" class="form-col">
            <el-input-number
              v-model="form.expressReward"
              :min="0"
              :precision="2"
              :step="5"
              placeholder="跑腿费用"
              size="large"
              style="width: 100%"
            >
              <template #prefix>&yen;</template>
            </el-input-number>
          </el-form-item>
        </div>
      </template>

      <!-- ── TUTORING 学习辅导 ── -->
      <template v-if="taskType === 'TUTORING'">
        <div class="form-row">
          <el-form-item label="辅导科目" prop="subject" class="form-col">
            <el-input v-model="form.subject" placeholder="例如：高等数学、大学英语..." maxlength="50" size="large" />
          </el-form-item>
          <el-form-item label="时薪报酬" prop="pricePerHour" class="form-col">
            <el-input-number
              v-model="form.pricePerHour"
              :min="0"
              :precision="2"
              :step="10"
              placeholder="每小时报酬"
              size="large"
              style="width: 100%"
            >
              <template #prefix>&yen;</template>
            </el-input-number>
          </el-form-item>
        </div>
        <el-form-item label="约定辅导时长（小时）" prop="durationHours">
          <el-input-number v-model="form.durationHours" :min="0.01" :max="1000" :precision="2" />
          <span style="margin-left:12px">约定总价：¥{{ tutoringQuote }}</span>
        </el-form-item>
        <el-form-item label="辅导地点" prop="tutoringLocation">
          <el-input v-model="form.tutoringLocation" placeholder="例如：图书馆3楼、教学楼A201" maxlength="100" size="large">
            <template #prefix>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
            </template>
          </el-input>
        </el-form-item>
      </template>

      <!-- ── FLEA 二手交易 ── -->
      <template v-if="taskType === 'FLEA'">
        <el-form-item label="交易类型" prop="tradeMode">
          <el-radio-group v-model="form.tradeMode" size="large">
            <el-radio-button value="SELL">出物</el-radio-button>
            <el-radio-button value="BUY">收物</el-radio-button>
          </el-radio-group>
        </el-form-item>


          <div class="form-row">
            <el-form-item :label="form.tradeMode === 'BUY' ? '求购预算' : '出售价格'" prop="itemPrice" class="form-col">
              <el-input-number
                  v-model="form.itemPrice"
                  :min="0"
                  :precision="2"
                  :step="10"
                  placeholder="请输入金额"
                  size="large"
                  style="width: 100%"
              >
                <template #prefix>&yen;</template>
              </el-input-number>
            </el-form-item>
          </div>

      </template>

      <!-- ── TEAM 活动组队 ── -->
      <template v-if="taskType === 'TEAM'">
        <el-form-item label="组队人数上限" prop="maxMembers">
          <el-input-number
            v-model="form.maxMembers"
            :min="2"
            :max="99"
            :step="1"
            placeholder="成团人数"
            size="large"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="活动地点（可选）">
          <el-input v-model="form.location" placeholder="例如：体育馆、二食堂门口" maxlength="100" size="large">
            <template #prefix>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
            </template>
          </el-input>
        </el-form-item>
      </template>

      <!-- ── CONSULTING 信息咨询 ── -->
      <template v-if="taskType === 'CONSULTING'">
        <div class="form-row">
          <el-form-item label="咨询方式" prop="consultingMode" class="form-col">
            <el-radio-group v-model="form.consultingMode" size="large">
              <el-radio-button value="ONLINE">线上咨询</el-radio-button>
              <el-radio-button value="OFFLINE">线下咨询</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="咨询报酬" prop="reward" class="form-col">
            <el-input-number
                v-model="form.reward"
                :min="0"
                :precision="2"
                :step="5"
                placeholder="报酬(0代表免费)"
                size="large"
                style="width: 100%"
            >
              <template #prefix>&yen;</template>
            </el-input-number>
          </el-form-item>
        </div>
        <el-form-item label="指定地点（可选）" prop="location" v-if="form.consultingMode === 'OFFLINE'">
          <el-input v-model="form.location" placeholder="例如：咖啡厅、图书馆..." maxlength="100" size="large">
            <template #prefix>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
            </template>
          </el-input>
        </el-form-item>
      </template>
    </div>

    <!-- 提交按钮 -->
    <div class="form-actions">
      <el-button size="large" @click="$emit('cancel')">{{ editing ? '取消修改' : '返回选择类型' }}</el-button>
      <el-button type="primary" size="large" :loading="submitting" @click="submitForm">
        <svg v-if="!submitting" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:18px;height:18px;margin-right:6px">
          <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
        </svg>
        {{ submitting ? '提交中…' : (editing ? '重新提交审核' : '提交审核') }}
      </el-button>
    </div>
  </el-form>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import taskApi from '@/task/taskApi'
import { CAMPUSES, TYPE_CATEGORY_ID } from '@/common/labels'
import { businessTime } from '@/common/format'
import { tutoringTotal } from '@/order/money'

/**
 * taskId 与 initial 同时传入时为编辑模式（API 契约 3.6 修改并重新提交）：
 * 回填原需求、提交走 PUT，且不读写本地草稿，避免覆盖用户正在写的新需求草稿。
 */
const props = defineProps({
  taskType: { type: String, required: true },
  taskId: { type: Number, default: null },
  initial: { type: Object, default: null }
})
const editing = computed(() => props.taskId != null && props.initial != null)

const emit = defineEmits(['submitted', 'cancel'])

const formRef = ref(null)
const submitting = ref(false)
const tutoringQuote = computed(() => {
  try { return tutoringTotal(form.pricePerHour, form.durationHours) }
  catch { return '—' }
})
// 各类型占位符文本
const titlePlaceholder = computed(() => {
  const map = {
    RUNNING: '例如：帮忙取快递、代买奶茶...',
    TUTORING: '例如：求高数辅导、C语言答疑...',
    FLEA: '例如：出二手教材、闲置耳机...',
    TEAM: '例如：组队参加数学建模、周末羽毛球...',
    CONSULTING: '例如：考研经验咨询、大厂实习内推咨询...'
  }
  return map[props.taskType] || '请输入需求标题'
})

const descPlaceholder = computed(() => {
  const map = {
    RUNNING: '描述取件/代买的具体要求，如快递公司、物品重量等...',
    TUTORING: '描述辅导的具体需求，如希望达到的目标、基础情况等...',
    FLEA: '描述物品的具体情况，如使用时长、瑕疵、保修状态等...',
    TEAM: '描述活动详情，如时间安排、活动内容、技能要求等...',
    CONSULTING: '描述你需要咨询的具体问题，或者你能提供的专业咨询服务内容...'
  }
  return map[props.taskType] || '详细描述你的需求，包括具体要求和注意事项...'
})

const typeSectionTitle = computed(() => {
  const map = {
    RUNNING: '跑腿详情',
    TUTORING: '辅导详情',
    FLEA: '交易详情',
    TEAM: '组队详情',
    CONSULTING: '咨询详情'
  }
  return map[props.taskType] || '详细信息'
})

// 表单数据
const form = reactive(getInitialForm())

function getInitialForm() {
  const base = {
    title: '',
    description: '',
    campus: '',
    deadline: null,
    reward: null,
    location: '',
    // RUNNING
    pickupLocation: '',
    deliveryLocation: '',
    pickupCode: '',
    expressReward: null,
    // TUTORING
    subject: '',
    pricePerHour: null,
    durationHours: 1,
    tutoringLocation: '',
    // FLEA
    tradeMode: 'SELL',
    itemPrice: null,
    // TEAM
    maxMembers: 2,
    // CONSULTING
    consultingMode: 'ONLINE'
  }
  return base
}

// 动态校验规则
const rules = computed(() => {
  const base = {
    title: [
      { required: true, message: '请输入需求标题', trigger: 'blur' },
      { min: 5, max: 50, message: '标题长度需在5-50字符之间', trigger: 'blur' }
    ],
    description: [
      { required: true, message: '请输入需求描述', trigger: 'blur' },
      { min: 10, max: 500, message: '描述长度需在10-500字符之间', trigger: 'blur' }
    ],
    campus: [{ required: true, message: '请选择校区', trigger: 'change' }],
    deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }]
  }

  if (props.taskType === 'RUNNING') {
    return {
      ...base,
      pickupLocation: [{ required: true, message: '请输入取货地点', trigger: 'blur' }],
      deliveryLocation: [{ required: true, message: '请输入送达地点', trigger: 'blur' }],
      expressReward: [
        { required: true, message: '请输入跑腿报酬', trigger: 'blur' },
        { type: 'number', min: 0, message: '报酬不能小于0', trigger: 'blur' }
      ]
    }
  }
  if (props.taskType === 'TUTORING') {
    return {
      ...base,
      subject: [{ required: true, message: '请输入辅导科目', trigger: 'blur' }],
      pricePerHour: [
        { required: true, message: '请输入时薪', trigger: 'blur' },
        { type: 'number', min: 0, message: '时薪不能小于0', trigger: 'blur' }
      ],
      tutoringLocation: [{ required: true, message: '请输入辅导地点', trigger: 'blur' }]
    }
  }
  if (props.taskType === 'FLEA') {
    return {
      ...base,
      tradeMode: [{ required: true, message: '请选择交易类型', trigger: 'change' }],
      // 出售与求购都需要约定总价：后端以 itemPrice 作为订单金额，缺失会被拒绝
      itemPrice: [
        { required: true, message: form.tradeMode === 'BUY' ? '请输入求购预算' : '请输入出售价格', trigger: 'blur' },
        { type: 'number', min: 0, message: '金额不能小于0', trigger: 'blur' }
      ]
    }
  }
  if (props.taskType === 'TEAM') {
    return {
      ...base,
      maxMembers: [
        { required: true, message: '请设置组队人数', trigger: 'blur' },
        { type: 'number', min: 2, max: 99, message: '人数需在2-99之间', trigger: 'blur' }
      ]
    }
  }
  if (props.taskType === 'CONSULTING') {
    return {
      ...base,
      consultingMode: [{ required: true, message: '请选择咨询方式', trigger: 'change' }]
    }
  }
  return base
})

function beforeToday(date) {
  return date.getTime() < Date.now() - 86400000
}

// ── 草稿自动保存与恢复 ──
const DRAFT_KEY = 'campusHubTaskDraft'

let draftTimer = null
watch(form, () => {
  if (editing.value) return
  clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    const draft = { ...form, _taskType: props.taskType }
    localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
  }, 500)
}, { deep: true })

/** 用原需求回填表单；原截止时间已过的清空，让发布者重新选择（后端要求截止时间晚于当前）。 */
function fillFromInitial(t) {
  Object.keys(form).forEach(k => {
    if (t[k] !== undefined && t[k] !== null) form[k] = t[k]
  })
  const deadlineMs = businessTime(t.deadline)
  form.deadline = Number.isFinite(deadlineMs) && deadlineMs > Date.now() ? String(t.deadline).slice(0, 19) : null
}

onMounted(() => {
  if (editing.value) {
    fillFromInitial(props.initial)
    return
  }
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const draft = JSON.parse(raw)
    if (draft._taskType !== props.taskType) return
    Object.keys(form).forEach(k => {
      if (k in draft && !k.startsWith('_')) form[k] = draft[k]
    })
  } catch { /* ignore corrupt draft */ }
})

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善表单信息后再提交')
    return
  }

  submitting.value = true
  try {
    // 构建请求体：基类字段 + taskType
    const payload = {
      title: form.title,
      description: form.description,
      // 编辑时分类与类型都不能改，沿用原需求的分类
      categoryId: editing.value ? props.initial.categoryId : categoryIdForType(props.taskType),
      taskType: props.taskType,
      campus: form.campus || null,
      reward: props.taskType !== 'TEAM' ? (form.reward ?? null) : null,
      location: props.taskType !== 'FLEA' ? (form.location || null) : null,
      deadline: form.deadline || null
    }

    // 子类专属字段
    if (props.taskType === 'RUNNING') {
      payload.pickupLocation = form.pickupLocation
      payload.deliveryLocation = form.deliveryLocation
      payload.pickupCode = form.pickupCode || null
      payload.expressReward = form.expressReward
    } else if (props.taskType === 'TUTORING') {
      payload.subject = form.subject
      payload.pricePerHour = form.pricePerHour
      payload.durationHours = form.durationHours
      payload.tutoringLocation = form.tutoringLocation
    } else if (props.taskType === 'FLEA') {
      payload.tradeMode = form.tradeMode
      payload.itemPrice = form.itemPrice
    } else if (props.taskType === 'TEAM') {
      payload.maxMembers = form.maxMembers
    } else if (props.taskType === 'CONSULTING') {
      payload.consultingMode = form.consultingMode
    }

    if (editing.value) {
      await taskApi.update(props.taskId, payload)
      ElMessage.success('已重新提交，等待管理员审核')
      emit('submitted', props.taskId)
      return
    }
    const res = await taskApi.create(payload)
    ElMessage.success('需求已提交，等待管理员审核')
    clearDraft()
    const taskId = res.data?.data?.id
    emit('submitted', taskId)
  } catch {
    // 错误提示由 http.js 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/** 发布类型对应的分类 ID，见 common/labels 的 TYPE_CATEGORY_ID。 */
function categoryIdForType(taskType) {
  return TYPE_CATEGORY_ID[taskType] || TYPE_CATEGORY_ID.RUNNING
}
</script>

<style scoped>
.form-section + .form-section {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
}
.section-title {
  margin: 0 0 16px;
  font-size: var(--fs-lg);
  font-weight: 600;
}
.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}
.form-actions .el-button { min-width: 112px; }
:deep(.el-form-item__label) { font-weight: 500; color: var(--ink); }
@media (max-width: 600px) {
  .form-row { grid-template-columns: 1fr; }
}
</style>
