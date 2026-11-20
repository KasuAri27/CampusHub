<template>
  <el-dialog v-model="visible" title="评价服务方" width="420px" append-to-body :close-on-click-modal="false">
    <div class="review-content">
      <div class="score-section">
        <div class="label">为这次服务打分</div>
        <el-rate
            v-model="score"
            :max="5"
            show-text
            :texts="['很差', '较差', '一般', '满意', '非常满意']"
            size="large"
        />
        <p class="rule">五星为对方加 1 分信用分，一、二星扣 1 分，三、四星不变。</p>
      </div>

      <el-form-item label="评价内容（可选）" label-position="top">
        <el-input
            v-model="comment"
            type="textarea"
            :rows="4"
            placeholder="说说这次服务的情况"
            maxlength="200"
            show-word-limit
        />
      </el-form-item>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitReview">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import reviewApi from '@/review/reviewApi'

const props = defineProps({
  orderId: Number,
  visible: Boolean
})
const emit = defineEmits(['update:visible', 'submitted'])

const visible = ref(props.visible)
const score = ref(5)
const comment = ref('')
const submitting = ref(false)

watch(() => props.visible, (val) => visible.value = val)
watch(visible, (val) => emit('update:visible', val))

const submitReview = async () => {
  if (!score.value) {
    ElMessage.warning('请先打分')
    return
  }
  submitting.value = true
  try {
    await reviewApi.submit(props.orderId, score.value, comment.value)
    ElMessage.success('评价已提交')
    emit('submitted')
    visible.value = false
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.review-content {
  padding: 10px 0;
}
.score-section {
  margin-bottom: 24px;
}
.label {
  font-size: var(--fs-md);
  color: var(--ink-2);
  margin-bottom: 8px;
}
.rule {
  margin-top: 8px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}
</style>