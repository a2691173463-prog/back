<template>
  <div class="question-bank-container">
    <div class="main-layout">
      <!-- 左侧题库列表 -->
      <div class="list-section glass-card">
        <div class="tab-header-row">
          <h2>经典面试题库</h2>
          <span class="subtitle">精选高频后端面试“八股文”，可直接发起针对性 AI 模拟对练</span>
        </div>

        <!-- 筛选栏 -->
        <div class="filter-bar">
          <el-input
            v-model="queryParams.keyword"
            placeholder="搜索题目名称或内容..."
            :prefix-icon="Search"
            clearable
            @change="handleSearch"
            class="filter-input"
          />
          <el-select v-model="queryParams.category" placeholder="选择分类" clearable @change="handleSearch" class="filter-select">
            <el-option label="Java" value="Java" />
            <el-option label="Spring" value="Spring" />
            <el-option label="Redis" value="Redis" />
            <el-option label="MySQL" value="MySQL" />
            <el-option label="计算机网络" value="计算机网络" />
            <el-option label="操作系统" value="操作系统" />
            <el-option label="系统设计" value="系统设计" />
          </el-select>
          <el-select v-model="queryParams.difficulty" placeholder="选择难度" clearable @change="handleSearch" class="filter-select">
            <el-option label="简单" value="Easy" />
            <el-option label="中等" value="Medium" />
            <el-option label="困难" value="Hard" />
          </el-select>
          <el-button type="primary" @click="handleSearch">筛选</el-button>
        </div>

        <!-- 列表表格 -->
        <el-table :data="questionList" style="width: 100%" v-loading="loading" class="custom-table">
          <el-table-column prop="title" label="题目名称" min-width="350">
            <template #default="scope">
              <span class="question-title-link" @click="viewDetails(scope.row)">{{ scope.row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" min-width="180">
            <template #default="scope">
              <el-tag size="small" type="info">{{ scope.row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" min-width="180">
            <template #default="scope">
              <el-tag size="small" :type="getDifficultyType(scope.row.difficulty)">
                {{ getDifficultyLabel(scope.row.difficulty) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="viewCount" label="浏览量" min-width="180" align="center">
            <template #default="scope">
              <span class="count-text">👁️ {{ scope.row.viewCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="interviewCount" label="对练数" min-width="180" align="center">
            <template #default="scope">
              <span class="count-text">⚡ {{ scope.row.interviewCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" @click="viewDetails(scope.row)">详情</el-button>
              <el-button type="success" size="small" @click="startInterview(scope.row.id)">对练</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="queryParams.page"
            v-model:page-size="queryParams.size"
            :total="total"
            layout="prev, pager, next"
            @current-change="fetchQuestions"
          />
        </div>
      </div>

      <!-- 右侧热门排行 -->
      <div class="sidebar-section">
        <div class="glass-card hot-card">
          <div class="hot-header">
            <h3>🔥 热门对练排行</h3>
          </div>
          <div class="hot-list" v-loading="hotLoading">
            <div
              v-for="(item, index) in hotQuestions"
              :key="item.id"
              class="hot-item"
              @click="viewDetails(item)"
            >
              <div :class="['rank-num', { 'top-three': index < 3 }]">{{ index + 1 }}</div>
              <div class="hot-info">
                <span class="hot-title">{{ item.title }}</span>
                <span class="hot-meta">{{ item.category }} · {{ item.interviewCount }}次对练</span>
              </div>
            </div>
            <div v-if="hotQuestions.length === 0" class="empty-text">暂无排行数据</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 题目详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="selectedQuestion?.title" width="750px" class="glass-dialog">
      <div v-if="selectedQuestion" class="question-detail-body">
        <div class="meta-row">
          <el-tag type="info">{{ selectedQuestion.category }}</el-tag>
          <el-tag :type="getDifficultyType(selectedQuestion.difficulty)">
            {{ getDifficultyLabel(selectedQuestion.difficulty) }}
          </el-tag>
          <span class="meta-stat">👁️ {{ selectedQuestion.viewCount }} 次浏览</span>
          <span class="meta-stat">⚡ {{ selectedQuestion.interviewCount }} 次对练</span>
        </div>

        <div class="section-box">
          <h4>📋 题目描述</h4>
          <p class="desc-text">{{ selectedQuestion.description }}</p>
        </div>

        <div class="section-box">
          <div class="answer-header">
            <h4>💡 参考答案</h4>
            <el-button type="primary" link @click="showAnswer = !showAnswer">
              {{ showAnswer ? '隐藏答案' : '显示参考答案' }}
            </el-button>
          </div>
          <div v-if="showAnswer" class="answer-text">
            {{ selectedQuestion.referenceAnswer }}
          </div>
          <div v-else class="answer-placeholder">
            答案已折叠，点击上方按钮查看。建议您先尝试进行 AI 模拟面试对练，再比对标准答案。
          </div>
        </div>

        <div class="action-row">
          <el-button type="success" size="large" class="start-btn" @click="startInterview(selectedQuestion.id)">
            ⚡ 开始此题 AI 模拟对练
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import axios from 'axios'

const emit = defineEmits(['start-interview'])

// 题库列表状态
const loading = ref(false)
const questionList = ref([])
const total = ref(0)
const queryParams = reactive({
  category: '',
  difficulty: '',
  keyword: '',
  page: 1,
  size: 10
})

// 热门排行状态
const hotLoading = ref(false)
const hotQuestions = ref<any[]>([])

// 详情弹窗状态
const detailVisible = ref(false)
const selectedQuestion = ref<any>(null)
const showAnswer = ref(false)

const fetchQuestions = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/questions/list', { params: queryParams })
    if (res.data.code === 200) {
      questionList.value = res.data.data.records
      total.value = res.data.data.total
    }
  } catch (e) {
    console.error('获取题库列表失败:', e)
  } finally {
    loading.value = false
  }
}

const fetchHotQuestions = async () => {
  hotLoading.value = true
  try {
    const res = await axios.get('/api/questions/hot')
    if (res.data.code === 200) {
      hotQuestions.value = res.data.data
    }
  } catch (e) {
    console.error('获取热门题库排行失败:', e)
  } finally {
    hotLoading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  fetchQuestions()
}

const viewDetails = async (row: any) => {
  selectedQuestion.value = row
  showAnswer.value = false
  detailVisible.value = true
  
  // 增加浏览量缓存自增计数
  try {
    await axios.get(`/api/questions/${row.id}`)
    // 自动累加本地显示
    row.viewCount++
  } catch (e) {
    console.error(e)
  }
}

const startInterview = (questionId: number) => {
  detailVisible.value = false
  emit('start-interview', questionId)
}

const getDifficultyLabel = (diff: string) => {
  if (diff === 'Easy') return '简单'
  if (diff === 'Medium') return '中等'
  if (diff === 'Hard') return '困难'
  return diff
}

const getDifficultyType = (diff: string) => {
  if (diff === 'Easy') return 'success'
  if (diff === 'Medium') return 'warning'
  if (diff === 'Hard') return 'danger'
  return 'info'
}

onMounted(() => {
  fetchQuestions()
  fetchHotQuestions()
})
</script>

<style scoped>
.question-bank-container {
  width: 100%;
}
.main-layout {
  display: flex;
  gap: 30px;
  min-height: calc(100vh - 120px); /* 撑满高度 */
}
.list-section {
  flex: 1;
  padding: 30px;
  display: flex;
  flex-direction: column;
}
.custom-table {
  flex: 1;
}
.sidebar-section {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.hot-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.hot-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tab-header-row {
  margin-bottom: 24px;
}
.tab-header-row h2 {
  font-size: 26px;
  font-weight: 700;
  color: #11111b;
}
.tab-header-row .subtitle {
  font-size: 15px;
  color: #6c7086;
}
.filter-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}
.filter-input {
  width: 260px;
}
.filter-select {
  width: 260px;
}
.custom-table :deep(.el-table__row) {
  height: 64px;
}
.custom-table :deep(th.el-table__cell) {
  background-color: rgba(244, 246, 249, 0.8) !important;
  font-weight: 700;
  color: #11111b;
  font-size: 15px;
}
.question-title-link {
  color: #aa3bff;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.2s;
  font-size: 15px;
}
.question-title-link:hover {
  color: #7928ca;
  text-decoration: underline;
}
.count-text {
  font-size: 14px;
  color: #585b70;
}
.pagination-container {
  margin-top: 25px;
  display: flex;
  justify-content: center;
}

/* 热门卡片 */
.hot-card {
  padding: 20px;
}
.hot-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: #11111b;
  margin-bottom: 15px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 10px;
}
.hot-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.hot-item {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: background 0.2s;
}
.hot-item:hover {
  background: rgba(170, 59, 255, 0.05);
}
.rank-num {
  width: 24px;
  height: 24px;
  border-radius: 12px;
  background: #e6e9ef;
  color: #4c4f69;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 13px;
  font-weight: 700;
}
.rank-num.top-three {
  background: linear-gradient(135deg, #aa3bff 0%, #6b3bff 100%);
  color: white;
}
.hot-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.hot-title {
  font-size: 14px;
  font-weight: 600;
  color: #313244;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-meta {
  font-size: 11px;
  color: #6c7086;
  margin-top: 2px;
}
.empty-text {
  text-align: center;
  color: #9399b2;
  font-size: 14px;
  padding: 20px 0;
}

/* 详情弹窗 */
.question-detail-body {
  padding: 10px 5px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.meta-stat {
  font-size: 13px;
  color: #6c7086;
}
.section-box {
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 20px;
}
.section-box h4 {
  font-size: 16px;
  font-weight: 700;
  color: #11111b;
  margin-bottom: 8px;
}
.desc-text {
  font-size: 15px;
  line-height: 1.6;
  color: #4c4f69;
  white-space: pre-wrap;
}
.answer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.answer-header h4 {
  margin-bottom: 0;
}
.answer-text {
  font-size: 15px;
  line-height: 1.6;
  color: #40a02b;
  white-space: pre-wrap;
  background: rgba(64, 160, 43, 0.03);
  padding: 12px;
  border-radius: 8px;
  border-left: 3px solid #40a02b;
}
.answer-placeholder {
  font-size: 14px;
  color: #9399b2;
  font-style: italic;
}
.action-row {
  text-align: center;
  margin-top: 25px;
}
.start-btn {
  padding: 12px 30px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  box-shadow: 0 4px 15px rgba(64, 191, 128, 0.2);
}
</style>
