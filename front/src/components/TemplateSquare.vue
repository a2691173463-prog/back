<template>
  <section class="template-library">
    <header class="library-header">
      <div>
        <p class="section-kicker">RESUME LIBRARY</p>
        <h2>简历模板</h2>
        <p class="header-copy">选择适合岗位的清晰版式，再上传现有简历进行内容诊断。</p>
      </div>
      <div class="library-summary" aria-label="模板统计">
        <strong>{{ templates.length }}</strong>
        <span>套可用模板</span>
      </div>
    </header>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        class="search-input"
        placeholder="搜索模板名称或适用岗位"
        aria-label="搜索简历模板"
      />
      <el-select v-model="selectedCategory" class="filter-select" aria-label="按岗位筛选">
        <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
      </el-select>
      <el-select v-model="sortBy" class="sort-select" aria-label="模板排序">
        <el-option label="最新上架" value="latest" />
        <el-option label="名称排序" value="name" />
        <el-option label="岗位分类" value="category" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" circle title="刷新模板" @click="fetchTemplates" />
    </div>

    <div class="result-row">
      <span>找到 {{ filteredTemplates.length }} 套模板</span>
      <span v-if="selectedCategory !== ALL_CATEGORIES">已筛选：{{ selectedCategory }}</span>
    </div>

    <div v-if="loading" class="templates-grid" aria-label="正在加载模板">
      <div v-for="index in 6" :key="index" class="template-card skeleton-card">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" class="skeleton-preview" />
            <div class="skeleton-copy">
              <el-skeleton-item variant="h3" style="width: 62%" />
              <el-skeleton-item variant="text" style="width: 92%" />
              <el-skeleton-item variant="text" style="width: 76%" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <div v-else-if="visibleTemplates.length" class="templates-grid">
      <article v-for="item in visibleTemplates" :key="item.id" class="template-card">
        <button class="preview-trigger" type="button" @click="openPreview(item)">
          <span class="format-badge">A4</span>
          <img
            v-if="hasRealPreview(item) && !failedImages.has(item.id)"
            :src="item.thumbnailUrl"
            class="template-image"
            :alt="`${item.name}预览`"
            @error="markImageFailed(item.id)"
          />
          <DocumentPreview v-else :template="item" />
          <span class="preview-action"><el-icon><View /></el-icon>查看大图</span>
        </button>

        <div class="template-details">
          <div class="template-meta">
            <span>{{ item.category || '通用' }}</span>
            <span>单页优先</span>
          </div>
          <h3>{{ item.name }}</h3>
          <p>{{ item.description || '结构清晰、便于招聘方快速浏览的标准简历模板。' }}</p>
          <div class="feature-list" aria-label="模板特点">
            <span v-for="feature in getHighlights(item)" :key="feature">{{ feature }}</span>
          </div>
          <div class="card-actions">
            <el-button :icon="Download" @click="downloadTemplate(item)">下载模板</el-button>
            <el-button type="primary" :icon="ArrowRight" @click="useTemplate(item)">在线制作</el-button>
          </div>
        </div>
      </article>
    </div>

    <el-empty v-else description="没有符合条件的模板">
      <el-button type="primary" @click="resetFilters">清除筛选</el-button>
    </el-empty>

    <el-pagination
      v-if="filteredTemplates.length > PAGE_SIZE"
      v-model:current-page="currentPage"
      class="template-pagination"
      background
      layout="prev, pager, next"
      :page-size="PAGE_SIZE"
      :total="filteredTemplates.length"
    />

    <el-dialog
      v-model="previewVisible"
      class="template-preview-dialog"
      width="min(960px, calc(100vw - 24px))"
      :show-close="true"
      destroy-on-close
    >
      <template #header>
        <div class="dialog-heading">
          <span>{{ selectedTemplate?.category || '通用模板' }}</span>
          <h3>{{ selectedTemplate?.name }}</h3>
        </div>
      </template>

      <div v-if="selectedTemplate" class="preview-layout">
        <div class="large-preview-shell">
          <img
            v-if="hasRealPreview(selectedTemplate) && !failedImages.has(selectedTemplate.id)"
            :src="selectedTemplate.thumbnailUrl"
            class="large-preview-image"
            :alt="`${selectedTemplate.name}完整预览`"
            @error="markImageFailed(selectedTemplate.id)"
          />
          <DocumentPreview v-else :template="selectedTemplate" large />
        </div>

        <aside class="preview-sidebar">
          <div>
            <span class="sidebar-label">模板说明</span>
            <p>{{ selectedTemplate.description || '适合校招和实习投递的标准单页简历版式。' }}</p>
          </div>
          <dl>
            <div><dt>适用方向</dt><dd>{{ selectedTemplate.category || '通用岗位' }}</dd></div>
            <div><dt>推荐页数</dt><dd>1 页</dd></div>
            <div><dt>文件格式</dt><dd>DOCX</dd></div>
          </dl>
          <div>
            <span class="sidebar-label">版式特点</span>
            <div class="sidebar-features">
              <span v-for="feature in getHighlights(selectedTemplate)" :key="feature">
                <el-icon><Check /></el-icon>{{ feature }}
              </span>
            </div>
          </div>
          <div class="preview-actions">
            <el-button :icon="Download" @click="downloadTemplate(selectedTemplate)">下载 Word</el-button>
            <el-button type="primary" :icon="ArrowRight" @click="useTemplate(selectedTemplate)">使用此模板</el-button>
          </div>
        </aside>
      </div>
    </el-dialog>

    <el-dialog
      v-model="editorVisible"
      class="resume-builder-dialog"
      fullscreen
      :show-close="false"
      destroy-on-close
      append-to-body
    >
      <ResumeBuilder v-if="selectedTemplate" :template="selectedTemplate" @close="editorVisible = false" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { ArrowRight, Check, Download, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import ResumeBuilder from './ResumeBuilder.vue'

interface ResumeTemplate {
  id: number
  name: string
  category?: string
  thumbnailUrl?: string
  downloadUrl?: string
  description?: string
  createTime?: string
}

const ALL_CATEGORIES = '全部岗位'
const PAGE_SIZE = 9

const loading = ref(false)
const templates = ref<ResumeTemplate[]>([])
const keyword = ref('')
const selectedCategory = ref(ALL_CATEGORIES)
const sortBy = ref('latest')
const currentPage = ref(1)
const previewVisible = ref(false)
const selectedTemplate = ref<ResumeTemplate | null>(null)
const editorVisible = ref(false)
const failedImages = ref(new Set<number>())

const categories = computed(() => [
  ALL_CATEGORIES,
  ...Array.from(new Set(templates.value.map(item => item.category).filter(Boolean) as string[]))
])

const filteredTemplates = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const result = templates.value.filter(item => {
    const matchesCategory = selectedCategory.value === ALL_CATEGORIES || item.category === selectedCategory.value
    const searchable = `${item.name} ${item.category || ''} ${item.description || ''}`.toLowerCase()
    return matchesCategory && (!query || searchable.includes(query))
  })

  return [...result].sort((left, right) => {
    if (sortBy.value === 'name') return left.name.localeCompare(right.name, 'zh-CN')
    if (sortBy.value === 'category') return (left.category || '').localeCompare(right.category || '', 'zh-CN')
    return new Date(right.createTime || 0).getTime() - new Date(left.createTime || 0).getTime()
  })
})

const visibleTemplates = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredTemplates.value.slice(start, start + PAGE_SIZE)
})

watch([keyword, selectedCategory, sortBy], () => {
  currentPage.value = 1
})

const fetchTemplates = async () => {
  loading.value = true
  try {
    const response = await axios.get('/api/templates/list')
    if (response.data.code !== 200) throw new Error(response.data.message || '请求失败')
    templates.value = response.data.data || []
  } catch (error) {
    console.error('获取模板列表失败:', error)
    ElMessage.error('模板加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const hasRealPreview = (item: ResumeTemplate) => {
  const url = item.thumbnailUrl || ''
  return Boolean(url) && !url.includes('icons8.com')
}

const markImageFailed = (id: number) => {
  failedImages.value = new Set([...failedImages.value, id])
}

const getHighlights = (item: ResumeTemplate) => {
  const category = item.category || ''
  if (/PM|产品/i.test(category)) return ['量化成果', '协作能力', '重点清晰']
  if (/设计/i.test(category)) return ['作品突出', '视觉有序', '阅读流畅']
  return ['项目突出', 'ATS 友好', '适合校招']
}

const openPreview = (item: ResumeTemplate) => {
  selectedTemplate.value = item
  previewVisible.value = true
}

const downloadTemplate = (item: ResumeTemplate) => {
  if (!item.downloadUrl) {
    ElMessage.warning('该模板暂未配置下载文件')
    return
  }
  window.open(item.downloadUrl, '_blank', 'noopener,noreferrer')
}

const useTemplate = (item: ResumeTemplate | null) => {
  if (!item) return
  selectedTemplate.value = item
  previewVisible.value = false
  editorVisible.value = true
}

const resetFilters = () => {
  keyword.value = ''
  selectedCategory.value = ALL_CATEGORIES
  sortBy.value = 'latest'
}

const DocumentPreview = defineComponent({
  name: 'DocumentPreview',
  props: {
    template: { type: Object as () => ResumeTemplate, required: true },
    large: Boolean
  },
  setup(props) {
    return () => h('div', {
      class: ['document-preview', { large: props.large, product: /PM|产品/i.test(props.template.category || '') }]
    }, [
      h('div', { class: 'document-name' }, '姓名 NAME'),
      h('div', { class: 'document-role' }, props.template.category || '求职方向'),
      h('div', { class: 'contact-lines' }, [h('i'), h('i'), h('i')]),
      h('section', [h('b', '教育经历'), h('span'), h('span')]),
      h('section', [h('b', '项目经历'), h('span', { class: 'long' }), h('span'), h('span', { class: 'medium' })]),
      h('section', [h('b', /PM|产品/i.test(props.template.category || '') ? '实践经历' : '实习经历'), h('span', { class: 'long' }), h('span', { class: 'medium' })]),
      h('section', [h('b', '专业技能'), h('span'), h('span', { class: 'long' })]),
      h('section', [h('b', '荣誉奖项'), h('span', { class: 'medium' })])
    ])
  }
})

onMounted(fetchTemplates)
</script>

<style scoped>
.template-library {
  width: 100%;
  min-height: calc(100vh - 96px);
  padding: 8px 4px 48px;
  color: #202124;
}

.library-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 10px 0 24px;
  border-bottom: 1px solid #e4e7ec;
}

.section-kicker {
  margin: 0 0 6px;
  color: #7755b7;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.library-header h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
  letter-spacing: 0;
}

.header-copy {
  margin: 8px 0 0;
  color: #69707d;
  font-size: 14px;
}

.library-summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
  white-space: nowrap;
  color: #69707d;
}

.library-summary strong {
  color: #202124;
  font-size: 24px;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 160px 140px 40px;
  gap: 10px;
  padding: 20px 0 10px;
}

.search-input,
.filter-select,
.sort-select {
  width: 100%;
}

.result-row {
  display: flex;
  justify-content: space-between;
  min-height: 32px;
  color: #7a818e;
  font-size: 13px;
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.template-card {
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dde1e7;
  border-radius: 8px;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.template-card:hover {
  border-color: #b8a5da;
  box-shadow: 0 10px 24px rgba(35, 28, 47, 0.08);
  transform: translateY(-2px);
}

.preview-trigger {
  position: relative;
  width: 100%;
  height: 286px;
  padding: 22px;
  overflow: hidden;
  border: 0;
  border-bottom: 1px solid #e6e9ee;
  background: #eef0f3;
  cursor: pointer;
}

.format-badge {
  position: absolute;
  z-index: 2;
  top: 12px;
  right: 12px;
  padding: 3px 7px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.94);
  color: #5f6672;
  font-size: 11px;
  font-weight: 700;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.template-image,
.document-preview {
  width: 168px;
  height: 238px;
  margin: 0 auto;
  object-fit: contain;
  background: #fff;
  box-shadow: 0 5px 15px rgba(26, 30, 38, 0.14);
}

.preview-action {
  position: absolute;
  left: 50%;
  bottom: 18px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #202124;
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transform: translate(-50%, 8px);
  transition: opacity 0.2s, transform 0.2s;
}

.preview-trigger:hover .preview-action,
.preview-trigger:focus-visible .preview-action {
  opacity: 1;
  transform: translate(-50%, 0);
}

.template-details {
  padding: 17px;
}

.template-meta {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.template-meta span,
.feature-list span {
  padding: 3px 7px;
  border-radius: 4px;
  background: #f0edf7;
  color: #694d9e;
  font-size: 11px;
}

.template-meta span:last-child {
  background: #eaf4ee;
  color: #347650;
}

.template-details h3 {
  min-height: 24px;
  margin: 0;
  overflow: hidden;
  color: #25272b;
  font-size: 17px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-details > p {
  display: -webkit-box;
  min-height: 42px;
  margin: 8px 0 12px;
  overflow: hidden;
  color: #69707d;
  font-size: 13px;
  line-height: 21px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.feature-list {
  display: flex;
  min-height: 23px;
  gap: 6px;
  margin-bottom: 16px;
  overflow: hidden;
}

.feature-list span {
  background: #f4f5f7;
  color: #626975;
  white-space: nowrap;
}

.card-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.card-actions .el-button,
.preview-actions .el-button {
  min-width: 0;
  margin: 0;
}

.skeleton-card {
  min-height: 440px;
}

.skeleton-preview {
  width: 100%;
  height: 286px;
  border-radius: 0;
}

.skeleton-copy {
  display: grid;
  gap: 13px;
  padding: 20px;
}

.template-pagination {
  justify-content: center;
  margin-top: 28px;
}

.dialog-heading span,
.sidebar-label {
  color: #7755b7;
  font-size: 12px;
  font-weight: 700;
}

.dialog-heading h3 {
  margin: 3px 0 0;
  font-size: 20px;
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(380px, 1.45fr) minmax(250px, 0.8fr);
  gap: 28px;
}

.large-preview-shell {
  display: flex;
  min-height: 610px;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: #e9ebef;
}

.large-preview-image,
.document-preview.large {
  width: min(100%, 410px);
  height: auto;
  aspect-ratio: 210 / 297;
  object-fit: contain;
  background: #fff;
  box-shadow: 0 8px 28px rgba(24, 27, 34, 0.16);
}

.preview-sidebar {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 8px 4px 4px 0;
}

.preview-sidebar p {
  margin: 7px 0 0;
  color: #626975;
  font-size: 14px;
  line-height: 1.7;
}

.preview-sidebar dl {
  margin: 0;
  border-top: 1px solid #e5e7eb;
}

.preview-sidebar dl div {
  display: flex;
  justify-content: space-between;
  padding: 11px 0;
  border-bottom: 1px solid #e5e7eb;
  font-size: 13px;
}

.preview-sidebar dt {
  color: #7a818e;
}

.preview-sidebar dd {
  margin: 0;
  color: #292c31;
  font-weight: 600;
}

.sidebar-features {
  display: grid;
  gap: 9px;
  margin-top: 10px;
}

.sidebar-features span {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #535a66;
  font-size: 13px;
}

.sidebar-features .el-icon {
  color: #3c8b5c;
}

.preview-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 9px;
  margin-top: auto;
}

.document-preview {
  box-sizing: border-box;
  padding: 18px 15px;
  color: #26313a;
  text-align: left;
}

.document-preview.large {
  padding: 45px 38px;
}

.document-name {
  color: #202a33;
  font-size: 12px;
  font-weight: 800;
}

.document-preview.large .document-name {
  font-size: 24px;
}

.document-role {
  margin-top: 2px;
  color: #3c7f67;
  font-size: 6px;
  font-weight: 700;
}

.document-preview.product .document-role,
.document-preview.product section b {
  color: #8162a6;
}

.document-preview.large .document-role {
  margin-top: 5px;
  font-size: 12px;
}

.contact-lines {
  display: flex;
  gap: 4px;
  margin: 8px 0 14px;
}

.contact-lines i {
  width: 24%;
  height: 2px;
  background: #c8cdd2;
}

.document-preview.large .contact-lines {
  gap: 10px;
  margin: 18px 0 34px;
}

.document-preview.large .contact-lines i {
  height: 4px;
}

.document-preview section {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 12px;
}

.document-preview section b {
  padding-bottom: 3px;
  border-bottom: 1px solid #3c7f67;
  color: #3c7f67;
  font-size: 6px;
}

.document-preview section span {
  width: 72%;
  height: 3px;
  background: #cbd1d7;
}

.document-preview section span.long {
  width: 100%;
}

.document-preview section span.medium {
  width: 86%;
}

.document-preview.large section {
  gap: 11px;
  margin-top: 30px;
}

.document-preview.large section b {
  padding-bottom: 8px;
  font-size: 13px;
}

.document-preview.large section span {
  height: 7px;
}

@media (max-width: 1100px) {
  .templates-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .template-library {
    padding-bottom: 32px;
  }

  .library-header {
    align-items: flex-start;
  }

  .library-summary {
    display: none;
  }

  .filter-bar {
    grid-template-columns: 1fr 1fr 40px;
  }

  .search-input {
    grid-column: 1 / -1;
  }

  .templates-grid {
    grid-template-columns: 1fr;
  }

  .preview-layout {
    grid-template-columns: 1fr;
  }

  .large-preview-shell {
    min-height: 0;
    padding: 18px;
  }

  .preview-sidebar {
    padding: 0;
  }
}

@media (max-width: 430px) {
  .filter-bar {
    grid-template-columns: 1fr 40px;
  }

  .sort-select {
    grid-column: 1;
  }

  .filter-select {
    grid-column: 1 / -1;
  }

  .card-actions {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.resume-builder-dialog.el-dialog {
  padding: 0;
  background: #f1f3f5;
}

.resume-builder-dialog .el-dialog__header {
  display: none;
}

.resume-builder-dialog .el-dialog__body {
  padding: 0;
}
</style>
