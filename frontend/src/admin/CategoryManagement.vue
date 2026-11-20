<template>
  <div>
    <PageHeader title="分类管理" description="需求广场的分类筛选与评价开关。关闭评价的分类，订单验收后不进入评价流程。">
      <template #actions>
        <el-button type="primary" @click="openEditor()">新增分类</el-button>
      </template>
    </PageHeader>

    <div class="panel table-wrap">
      <el-table :data="categories" v-loading="loading" row-key="id">
        <el-table-column prop="sortOrder" label="排序" width="80" align="center">
          <template #default="{ row }"><span class="num">{{ row.sortOrder ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="发布类型" min-width="140">
          <template #default="{ row }">
            <span v-if="boundType(row.id)" class="bound">{{ boundType(row.id) }}</span>
            <span v-else class="muted">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="评价" width="110">
          <template #default="{ row }">
            <StatusTag :label="row.hasReview ? '验收后评价' : '不评价'" :tone="row.hasReview ? 'brand' : 'muted'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            <el-tooltip :disabled="!boundType(row.id)" content="与发布类型绑定，删除后该类型将无法发布" placement="top">
              <span class="tip-wrap">
                <el-button link type="danger" :disabled="Boolean(boundType(row.id))" @click="remove(row)">删除</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <p class="note">发布页的五种类型固定对应前五个分类；新增的分类会出现在广场筛选中，但暂时没有对应的发布入口。</p>

    <el-dialog v-model="editor.visible" :title="editor.id ? '编辑分类' : '新增分类'" width="440px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="名称" required>
          <el-input v-model.trim="editor.name" maxlength="20" show-word-limit placeholder="例如：快递代取" />
        </el-form-item>
        <el-form-item label="排序（数字越小越靠前）">
          <el-input-number v-model="editor.sortOrder" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="editor.hasReview" active-text="验收后允许评价" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" :loading="editor.saving" @click="save">保存分类</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import adminApi from '@/admin/adminApi'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import { TASK_TYPE, TYPE_CATEGORY_ID } from '@/common/labels'

/**
 * 分类字典管理（GET/POST/PUT/DELETE /v1/admin/categories）。
 * 前五个分类与发布类型绑定（见 TYPE_CATEGORY_ID），界面上禁止删除，避免对应类型无法发布；
 * 分类写操作目前仍由 AdminService 直写分类表，是否改走交易域见 TODO §4.1 K1 遗留项。
 */
const loading = ref(false)
const categories = ref([])
const editor = reactive({ visible: false, saving: false, id: null, name: '', sortOrder: 0, hasReview: false })

const typeByCategory = Object.fromEntries(Object.entries(TYPE_CATEGORY_ID).map(([type, id]) => [id, TASK_TYPE[type].name]))
function boundType(id) { return typeByCategory[id] || '' }

async function load() {
  loading.value = true
  try {
    const list = (await adminApi.listCategories()).data.data || []
    categories.value = list.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
  } catch {
    categories.value = []
  } finally {
    loading.value = false
  }
}

function openEditor(row) {
  Object.assign(editor, {
    visible: true,
    saving: false,
    id: row?.id ?? null,
    name: row?.name ?? '',
    sortOrder: row?.sortOrder ?? (categories.value.length + 1),
    hasReview: row?.hasReview ?? false
  })
}

async function save() {
  if (!editor.name) { ElMessage.warning('请填写分类名称'); return }
  editor.saving = true
  const body = { name: editor.name, sortOrder: editor.sortOrder, hasReview: editor.hasReview }
  try {
    if (editor.id) await adminApi.updateCategory(editor.id, body)
    else await adminApi.createCategory(body)
    ElMessage.success('分类已保存')
    editor.visible = false
    await load()
  } catch {
    // 拦截器已提示错误，保留弹窗内容便于修改后重试
  } finally {
    editor.saving = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除分类「${row.name}」？已有需求使用该分类时会删除失败。`, '删除分类', { type: 'warning', confirmButtonText: '删除分类', cancelButtonText: '取消' })
  } catch { return }
  try {
    await adminApi.deleteCategory(row.id)
    ElMessage.success('分类已删除')
    await load()
  } catch { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.table-wrap { padding: 4px 8px; }
.bound {
  padding: 1px 8px;
  border-radius: var(--r-sign);
  background: var(--mint-wash);
  color: var(--pine);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.muted { color: var(--ink-3); font-size: var(--fs-sm); }
.tip-wrap { display: inline-block; margin-left: 12px; }
.note { margin-top: 12px; color: var(--ink-3); font-size: var(--fs-sm); }
</style>
