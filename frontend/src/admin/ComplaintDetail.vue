<template>
  <div v-loading="loading">
    <button class="back-link" type="button" @click="$router.back()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M15 18l-6-6 6-6"/></svg>
      返回
    </button>
    <PageHeader title="投诉详情" description="核对双方说法与订单记录；涉及付款义务的争议在“交易与结算”中裁决。">
      <template #actions>
        <el-button @click="$router.push('/admin/finance?view=dispute')">去裁决争议</el-button>
      </template>
    </PageHeader>

    <div v-if="complaint" class="panel body">
      <dl class="facts">
        <dt>投诉编号</dt><dd class="num">#{{ complaint.id }}</dd>
        <dt>关联订单</dt><dd class="num">#{{ complaint.orderId }}</dd>
        <dt>投诉人</dt><dd>用户 #{{ complaint.complainantId }}</dd>
        <dt>被投诉人</dt><dd>用户 #{{ complaint.targetId }}</dd>
        <dt>提交时间</dt><dd>{{ formatDateTime(complaint.createTime) }}</dd>
        <dt>原因</dt><dd><StatusTag :label="complaint.reason" tone="danger" /></dd>
        <dt>具体情况</dt><dd class="desc">{{ complaint.description || '未填写' }}</dd>
      </dl>
      <div v-if="complaint.imageUrl" class="evidence">
        <p>证据图片</p>
        <el-image :src="assetUrl(complaint.imageUrl)" :preview-src-list="[assetUrl(complaint.imageUrl)]" fit="contain" preview-teleported />
      </div>
    </div>
    <EmptyState v-else-if="!loading" title="找不到这条投诉" description="编号可能有误，或投诉记录已被清理。" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import http from '@/common/http'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import EmptyState from '@/common/ui/EmptyState.vue'
import { formatDateTime } from '@/common/format'
import { assetUrl } from '@/common/assetUrl'

/** 投诉详情（GET /v1/complaints/{id}，仅订单当事人与管理员可查）。证据图片走同源相对地址（K6）。 */
const route = useRoute()
const loading = ref(true)
const complaint = ref(null)

onMounted(async () => {
  try {
    complaint.value = (await http.get(`/complaints/${route.params.id}`)).data?.data || null
  } catch {
    complaint.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.back-link { margin-bottom: 8px; }
.body { padding: 20px 22px; }
.facts {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 10px 16px;
  margin: 0;
}
.facts dt { color: var(--ink-2); font-size: var(--fs-sm); }
.facts dd { margin: 0; }
.desc { white-space: pre-wrap; max-width: 64ch; }
.evidence { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--line); }
.evidence p { color: var(--ink-2); font-size: var(--fs-sm); margin-bottom: 8px; }
.evidence .el-image { max-width: 100%; max-height: 420px; border: 1px solid var(--line); border-radius: 8px; }
</style>
