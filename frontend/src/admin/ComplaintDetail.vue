<template>
  <div class="complaint-detail" v-loading="loading">
    <button class="back-btn" @click="$router.push('/admin')">← 返回</button>
    <h2>投诉详情</h2>
    <el-card v-if="complaint" shadow="never" style="margin-top:16px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="投诉ID">{{ complaint.id }}</el-descriptions-item>
        <el-descriptions-item label="投诉人ID">{{ complaint.complainantId }}</el-descriptions-item>
        <el-descriptions-item label="被投诉人ID">{{ complaint.targetId }}</el-descriptions-item>
        <el-descriptions-item label="关联订单ID">{{ complaint.orderId }}</el-descriptions-item>
        <el-descriptions-item label="投诉理由">{{ complaint.reason }}</el-descriptions-item>
        <el-descriptions-item label="详细描述">{{ complaint.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ complaint.createTime }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="complaint.imageUrl" style="margin-top:16px">
        <h4>证据图片</h4>
        <img :src="'http://localhost:8080' + complaint.imageUrl"
          style="max-width:100%;max-height:400px;border:1px solid #ddd;border-radius:8px" />
      </div>
    </el-card>
    <el-empty v-else description="投诉不存在" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import http from '@/common/http'

const route = useRoute()
const loading = ref(true)
const complaint = ref(null)

onMounted(async () => {
  try {
    const res = await http.get(`/complaints/${route.params.id}`)
    complaint.value = res.data?.data
  } finally { loading.value = false }
})
</script>

<style scoped>
.complaint-detail { max-width: 800px; margin: 0 auto; }
.back-btn { border: none; background: none; color: #409eff; cursor: pointer; font-size: 14px; padding: 0; margin-bottom: 8px; }
</style>
