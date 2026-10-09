<template>
  <div class="admin-knowledge">
    <div class="metrics-row">
      <div class="metric"><span>公共文档</span><strong>{{ stats.global_documents || 0 }}</strong></div>
      <div class="metric"><span>岗位文档</span><strong>{{ stats.position_documents || 0 }}</strong></div>
      <div class="metric"><span>私人文档</span><strong>{{ stats.private_documents || 0 }}</strong></div>
      <div class="metric"><span>累计检索</span><strong>{{ stats.queries || 0 }}</strong></div>
      <div class="metric"><span>无命中查询</span><strong>{{ stats.no_hit_queries || 0 }}</strong></div>
      <div class="metric"><span>平均命中分</span><strong>{{ formatScore(stats.average_score) }}</strong></div>
    </div>

    <section class="upload-section">
      <div class="section-heading">
        <div><h3>上传共享知识文档</h3><p>公共文档对所有岗位可见，岗位文档仅参与对应岗位面试。</p></div>
      </div>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="文档标题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
          <el-form-item label="技术分类"><el-input v-model="form.category" maxlength="80" placeholder="例如 Redis" /></el-form-item>
          <el-form-item label="文档范围">
            <el-radio-group v-model="form.scope">
              <el-radio-button value="GLOBAL">公共知识库</el-radio-button>
              <el-radio-button value="POSITION">岗位知识包</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="目标岗位">
            <el-select v-model="form.positionCode" :disabled="form.scope === 'GLOBAL'" style="width: 100%">
              <el-option v-for="item in positions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-upload
          ref="uploadRef"
          drag action="#" accept="application/pdf,.pdf"
          :auto-upload="false" :limit="1"
          :on-change="handleFileChange" :on-remove="() => selectedFile = null"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖入 PDF，或点击选择文件</div>
        </el-upload>
        <div class="upload-actions">
          <span>仅支持文本 PDF，最大 100MB</span>
          <el-button type="primary" :icon="Upload" :loading="uploading" @click="uploadDocument">上传并向量化</el-button>
        </div>
      </el-form>
    </section>

    <section class="list-section">
      <div class="section-heading"><h3>共享知识文档</h3><el-button :icon="Refresh" circle @click="loadAll" /></div>
      <el-table :data="documents" v-loading="loading" empty-text="暂无共享知识文档">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="scope" label="范围" width="120">
          <template #default="scope"><el-tag :type="scope.row.scope === 'GLOBAL' ? 'success' : 'warning'">{{ scopeLabel(scope.row.scope) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="position_code" label="岗位" width="150"><template #default="scope">{{ positionLabel(scope.row.position_code) }}</template></el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="chunk_count" label="切片" width="80" align="center" />
        <el-table-column prop="retrieval_count" label="命中次数" width="100" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope"><el-button type="danger" :icon="Delete" circle plain @click="deleteDocument(scope.row)" /></template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Delete, Refresh, Upload, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadInstance } from 'element-plus'
import axios from 'axios'

const positions = [
  { label: '通用技术面试', value: 'GENERAL' },
  { label: 'Java 后端开发', value: 'JAVA_BACKEND' },
  { label: '前端开发', value: 'FRONTEND' },
  { label: 'AI Agent 开发', value: 'AI_AGENT' },
  { label: '测试开发', value: 'QA_ENGINEER' }
]
const documents = ref<any[]>([])
const stats = ref<any>({})
const loading = ref(false)
const uploading = ref(false)
const selectedFile = ref<File | null>(null)
const uploadRef = ref<UploadInstance>()
const form = reactive({ title: '', category: '通用', scope: 'GLOBAL', positionCode: 'GENERAL' })

watch(() => form.scope, value => { if (value === 'GLOBAL') form.positionCode = 'GENERAL' })
const positionLabel = (value: string) => positions.find(item => item.value === value)?.label || value || '通用'
const scopeLabel = (value: string) => value === 'GLOBAL' ? '公共' : '岗位包'
const formatScore = (value: unknown) => Number(value || 0).toFixed(3)

const loadAll = async () => {
  loading.value = true
  try {
    const [documentsResponse, statsResponse] = await Promise.all([
      axios.get('/api/admin/knowledge/documents'),
      axios.get('/api/admin/knowledge/stats')
    ])
    documents.value = documentsResponse.data.data || []
    stats.value = statsResponse.data.data || {}
  } catch {
    ElMessage.error('知识库数据加载失败')
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
  if (!form.title) form.title = file.name.replace(/\.pdf$/i, '')
}

const uploadDocument = async () => {
  if (!selectedFile.value) return ElMessage.warning('请选择 PDF 文件')
  if (selectedFile.value.size > 100 * 1024 * 1024) return ElMessage.warning('PDF 文件不能超过 100MB')
  const data = new FormData()
  data.append('file', selectedFile.value)
  data.append('title', form.title.trim())
  data.append('category', form.category.trim() || '通用')
  data.append('scope', form.scope)
  data.append('positionCode', form.positionCode)
  uploading.value = true
  try {
    const response = await axios.post('/api/admin/knowledge/documents', data)
    if (response.data.code !== 200) return ElMessage.error(response.data.message || '文档入库失败')
    ElMessage.success(response.data.data?.duplicate ? '相同文档已经入库' : '共享知识文档入库成功')
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    form.title = ''
    await loadAll()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '文档入库失败')
  } finally {
    uploading.value = false
  }
}

const deleteDocument = async (row: any) => {
  await ElMessageBox.confirm(`确认删除“${row.title}”？`, '删除共享知识文档', { type: 'warning' })
  const response = await axios.delete(`/api/admin/knowledge/documents/${row.id}`)
  if (response.data.code === 200) {
    ElMessage.success('已删除')
    await loadAll()
  }
}

onMounted(loadAll)
</script>

<style scoped>
.admin-knowledge { display: grid; gap: 20px; }
.metrics-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
.metric { border: 1px solid #e5e7eb; padding: 16px; background: #fff; border-radius: 6px; display: grid; gap: 8px; }
.metric span { color: #6b7280; font-size: 13px; }
.metric strong { color: #111827; font-size: 24px; }
.upload-section, .list-section { border-top: 1px solid #e5e7eb; padding-top: 18px; }
.section-heading, .upload-actions { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.section-heading { margin-bottom: 16px; }
.section-heading h3 { margin: 0; }
.section-heading p { color: #6b7280; margin: 5px 0 0; font-size: 13px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.upload-icon { font-size: 30px; color: #409eff; }
.upload-actions { margin-top: 14px; color: #6b7280; font-size: 13px; }
@media (max-width: 900px) { .metrics-row, .form-grid { grid-template-columns: 1fr 1fr; } }
</style>
