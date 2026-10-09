<template>
  <div class="knowledge-page">
    <section class="upload-panel glass-card">
      <div class="panel-heading">
        <div>
          <h2>面试知识库</h2>
          <p>公共知识库和岗位知识包由管理员维护，你也可以上传仅自己可见的私人文档。</p>
        </div>
        <el-button :icon="Refresh" circle title="刷新" @click="fetchDocuments" />
      </div>

      <el-form label-position="top" class="upload-form">
        <div class="form-grid">
          <el-form-item label="文档名称">
            <el-input v-model="form.title" maxlength="200" placeholder="例如：Redis 面试知识整理" />
          </el-form-item>
          <el-form-item label="技术分类">
            <el-input v-model="form.category" maxlength="80" placeholder="例如：Redis" />
          </el-form-item>
        </div>
        <el-upload
          ref="uploadRef"
          drag
          action="#"
          accept="application/pdf,.pdf"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          class="pdf-upload"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖入 PDF，或点击选择文件</div>
        </el-upload>
        <div class="upload-actions">
          <span class="file-rule">仅支持可提取文本的 PDF，最大 100MB</span>
          <el-button type="primary" :icon="Upload" :loading="uploading" @click="uploadDocument">
            上传并向量化
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="document-panel glass-card">
      <div class="list-heading">
        <h3>可用知识文档</h3>
        <span>{{ documents.length }} 份</span>
      </div>
      <el-table :data="documents" v-loading="loading" empty-text="暂未上传知识文档">
        <el-table-column prop="title" label="名称" min-width="220" />
        <el-table-column prop="scope" label="来源" width="110">
          <template #default="scope">
            <el-tag :type="scopeType(scope.row.scope)">{{ scopeLabel(scope.row.scope) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="position_code" label="适用岗位" width="140">
          <template #default="scope">{{ positionLabel(scope.row.position_code) }}</template>
        </el-table-column>
        <el-table-column prop="file_name" label="文件" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="140">
          <template #default="scope"><el-tag type="info">{{ scope.row.category }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="chunk_count" label="切片数" width="100" align="center" />
        <el-table-column prop="retrieval_count" label="命中次数" width="100" align="center" />
        <el-table-column label="入库时间" width="190">
          <template #default="scope">{{ formatTime(scope.row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.scope === 'PRIVATE'"
              type="danger"
              :icon="Delete"
              circle
              plain
              title="删除"
              @click="deleteDocument(scope.row)"
            />
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Refresh, Upload, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadInstance } from 'element-plus'
import axios from 'axios'

const documents = ref<any[]>([])
const loading = ref(false)
const uploading = ref(false)
const selectedFile = ref<File | null>(null)
const uploadRef = ref<UploadInstance>()
const form = reactive({ title: '', category: '通用' })
const positions = [
  { label: '通用', value: 'GENERAL' },
  { label: 'Java 后端', value: 'JAVA_BACKEND' },
  { label: '前端开发', value: 'FRONTEND' },
  { label: 'AI Agent', value: 'AI_AGENT' },
  { label: '测试开发', value: 'QA_ENGINEER' }
]

const scopeLabel = (value: string) => ({
  PRIVATE: '私人',
  GLOBAL: '公共',
  POSITION: '岗位包'
}[value] || value || '-')
const scopeType = (value: string) => value === 'PRIVATE' ? 'info' : value === 'GLOBAL' ? 'success' : 'warning'
const positionLabel = (value: string) => positions.find(item => item.value === value)?.label || value || '通用'

const fetchDocuments = async () => {
  loading.value = true
  try {
    const response = await axios.get('/api/knowledge/documents')
    if (response.data.code === 200) documents.value = response.data.data || []
  } catch {
    ElMessage.error('知识文档加载失败')
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
  if (!form.title && file.name) form.title = file.name.replace(/\.pdf$/i, '')
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const uploadDocument = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择 PDF 文件')
    return
  }
  if (selectedFile.value.size > 100 * 1024 * 1024) {
    ElMessage.warning('PDF 文件不能超过 100MB')
    return
  }
  const data = new FormData()
  data.append('file', selectedFile.value)
  data.append('title', form.title.trim())
  data.append('category', form.category.trim() || '通用')
  uploading.value = true
  try {
    const response = await axios.post('/api/knowledge/documents', data)
    if (response.data.code !== 200) {
      ElMessage.error(response.data.message || '知识文档入库失败')
      return
    }
    ElMessage.success(response.data.data?.duplicate ? '该文档已经入库' : '知识文档入库成功')
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    form.title = ''
    await fetchDocuments()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '知识文档入库失败')
  } finally {
    uploading.value = false
  }
}

const deleteDocument = async (row: any) => {
  await ElMessageBox.confirm(`确认删除“${row.title}”？`, '删除知识文档', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  const response = await axios.delete(`/api/knowledge/documents/${row.id}`)
  if (response.data.code === 200) {
    ElMessage.success('已删除')
    await fetchDocuments()
  }
}

const formatTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN') : '-'

onMounted(fetchDocuments)
</script>

<style scoped>
.knowledge-page { display: grid; gap: 16px; }
.glass-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; }
.upload-panel, .document-panel { padding: 24px; }
.panel-heading, .list-heading, .upload-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.panel-heading { margin-bottom: 20px; }
.panel-heading h2, .list-heading h3 { margin: 0; color: #17171f; }
.panel-heading p { margin: 6px 0 0; color: #6b7280; font-size: 14px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.pdf-upload { width: 100%; }
.pdf-upload :deep(.el-upload), .pdf-upload :deep(.el-upload-dragger) { width: 100%; }
.upload-icon { font-size: 30px; color: #409eff; }
.upload-actions { margin-top: 16px; }
.file-rule, .list-heading span { color: #6b7280; font-size: 13px; }
.list-heading { margin-bottom: 16px; }
@media (max-width: 760px) {
  .form-grid { grid-template-columns: 1fr; }
  .upload-actions { align-items: stretch; flex-direction: column; }
}
</style>
