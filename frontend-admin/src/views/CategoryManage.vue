<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { adminCategoryApi } from '@/api'

const list = ref([])
const dlg = reactive({ visible: false, title: '新增分类', form: {} })
const formRef = ref()
const rules = { name: [{ required: true, message: '请输入分类名', trigger: 'blur' }] }

const load = async () => {
  const r = await adminCategoryApi.list()
  list.value = r.data || []
}
// 图标标识是内部 slug，展示时给运营一个可读名称
const ICON_TEXT = {
  phone: '手机', computer: '电脑', home: '家电',
  shirt: '服饰', lipstick: '美妆', book: '图书'
}
const iconText = icon => ICON_TEXT[icon] || '自定义'
const onAdd = () => { dlg.title = '新增分类'; dlg.form = { name: '', icon: '', sortOrder: 0 }; dlg.visible = true }
const onEdit = (row) => { dlg.title = '编辑分类'; dlg.form = { ...row }; dlg.visible = true }

const onSubmit = async () => {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (dlg.form.id) await adminCategoryApi.update(dlg.form.id, dlg.form)
  else await adminCategoryApi.create(dlg.form)
  ElMessage.success('已保存')
  dlg.visible = false
  load()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？该分类下不能有商品。`, '删除确认', { type: 'warning' })
  await adminCategoryApi.delete(row.id)
  ElMessage.success('已删除')
  load()
}
const fmt = t => String(t || '').replace('T', ' ').slice(0, 16) || '—'

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">分类管理</h1>
      <p class="page-desc">共 {{ list.length }} 个分类 · 分类用于用户端的商品筛选</p>
    </div>

    <div class="card">
      <div class="card-head">
        <div class="card-title">分类列表</div>
        <el-button type="primary" :icon="Plus" @click="onAdd">新增分类</el-button>
      </div>

      <el-table :data="list" class="admin-table">
        <el-table-column label="编号" width="90">
          <template #default="{ row }"><span class="font-num text-xs">No.{{ String(row.id).padStart(2, '0') }}</span></template>
        </el-table-column>
        <el-table-column label="分类" min-width="220">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:12px">
              <span style="width:38px;height:38px;border-radius:var(--r-xs);display:flex;align-items:center;justify-content:center;font-family:var(--font-display);font-size:19px;background:var(--surface-2)">
                {{ (row.name || '类').trim()[0] }}
              </span>
              <span style="font-weight:600">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标标识" width="150">
          <template #default="{ row }">
            <div>{{ iconText(row.icon) }}</div>
            <div class="font-num text-xs text-muted">{{ row.icon || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90">
          <template #default="{ row }"><span class="font-num">{{ row.sortOrder }}</span></template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }"><span class="font-num text-xs">{{ fmt(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="onEdit(row)">编辑</el-button>
              <el-button class="act-del" text @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <div class="empty-icon" style="font-family:var(--font-display)">类</div>
            <div class="empty-title">暂无分类</div>
            <p class="empty-sub">点击「新增分类」创建第一个分类。</p>
          </div>
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.title" width="480px">
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name"><el-input v-model="dlg.form.name" placeholder="分类名称" /></el-form-item>
        <el-form-item label="图标标识"><el-input v-model="dlg.form.icon" placeholder="如 phone / computer" /></el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dlg.form.sortOrder" :min="0" />
          <span class="text-xs text-muted" style="margin-left:12px">数字越小越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
