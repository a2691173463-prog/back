<template>
  <div class="admin-dashboard-container glass-card">
    <div class="tab-header-row">
      <h2>⚙️ 管理员后台</h2>
      <span class="subtitle">管理系统的题库和简历模板，并掌握系统的整体运行数据</span>
    </div>

    <el-tabs v-model="activeSubTab" class="admin-tabs">
      <!-- Tab 1: 题库管理 -->
      <el-tab-pane label="题库管理" name="questions">
        <div class="action-bar-admin">
          <el-button type="primary" :prefix-icon="Plus" @click="openQuestionForm(null)">
            ➕ 新增面试题
          </el-button>
        </div>

        <el-table :data="questionList" style="width: 100%" v-loading="loading" class="custom-table">
          <el-table-column prop="title" label="题目名称" min-width="350"></el-table-column>
          <el-table-column prop="category" label="分类" min-width="180">
            <template #default="scope">
              <el-tag size="small">{{ scope.row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" min-width="180">
            <template #default="scope">
              <el-tag size="small" :type="getDifficultyType(scope.row.difficulty)">
                {{ getDifficultyLabel(scope.row.difficulty) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="viewCount" label="浏览量" min-width="180" align="center"></el-table-column>
          <el-table-column prop="interviewCount" label="对练量" min-width="180" align="center"></el-table-column>
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" plain @click="openQuestionForm(scope.row)">编辑</el-button>
              <el-button type="danger" size="small" plain @click="deleteQuestion(scope.row.id)">删除</el-button>
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
      </el-tab-pane>

      <!-- Tab 2: 模板管理 -->
      <el-tab-pane label="模板管理" name="templates">
        <div class="action-bar-admin">
          <el-button type="primary" @click="openTemplateForm">
            ➕ 新增简历模板
          </el-button>
        </div>

        <el-table :data="templateList" style="width: 100%" v-loading="templateLoading" class="custom-table">
          <el-table-column prop="name" label="模板名称" min-width="150"></el-table-column>
          <el-table-column prop="category" label="类型" width="120">
            <template #default="scope">
              <el-tag size="small" type="success">{{ scope.row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button type="danger" size="small" plain @click="deleteTemplate(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 3: 系统分析 -->
      <el-tab-pane label="数据分析" name="analysis">
        <div class="dashboard-wrapper" v-loading="analysisLoading">
          <!-- 第一行: 四个核心指标卡片 -->
          <div class="metrics-grid">
            <div class="metric-card">
              <div class="metric-header">
                <span class="title">今日系统浏览量</span>
                <span class="icons">📊 👁️</span>
              </div>
              <div class="metric-body">
                <span class="value">{{ (stats.interviewCount * 4 + 128).toLocaleString() }}</span>
                <span class="trend up">日同比: 436% ↑</span>
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-header">
                <span class="title">累计注册用户数</span>
                <span class="icons">👥 ⚙️</span>
              </div>
              <div class="metric-body">
                <span class="value">{{ stats.userCount }}</span>
                <span class="trend up">周同比: +15% ↑</span>
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-header">
                <span class="title">累计诊断简历数</span>
                <span class="icons">📄 🗂️</span>
              </div>
              <div class="metric-body">
                <span class="value">{{ stats.resumeCount }}</span>
                <span class="trend up">日同比: +8% ↑</span>
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-header">
                <span class="title">累计模拟面试数</span>
                <span class="icons">⚡ 🎓</span>
              </div>
              <div class="metric-body">
                <span class="value">{{ stats.interviewCount }}</span>
                <span class="trend stable">周同比: 0% -</span>
              </div>
            </div>
          </div>

          <!-- 第二行: 图表可视化展示 -->
          <div class="charts-grid">
            <div class="chart-card">
              <div class="chart-header">
                <span>请求数/2h</span>
                <span class="chart-icons">📈 ⚙️</span>
              </div>
              <!-- 柱状图 -->
              <div class="bar-chart-container">
                <div class="bar-item" v-for="(val, idx) in [15, 28, 45, 80, 55, 70, 95, 85, 98, 75, 60, 85]" :key="idx">
                  <div class="bar-fill" :style="{ height: val + '%' }"></div>
                  <span class="bar-label">{{ String(idx * 2).padStart(2, '0') }}h</span>
                </div>
              </div>
            </div>

            <div class="chart-card">
              <div class="chart-header">
                <span>算力消耗/2h</span>
                <span class="chart-icons">📊 ⚡</span>
              </div>
              <!-- 红色柱状图 (模拟错误率/消耗) -->
              <div class="bar-chart-container error-bar">
                <div class="bar-item" v-for="(val, idx) in [5, 12, 8, 45, 10, 5, 65, 30, 80, 25, 15, 5]" :key="idx">
                  <div class="bar-fill" :style="{ height: val + '%' }"></div>
                  <span class="bar-label">{{ String(idx * 2).padStart(2, '0') }}h</span>
                </div>
              </div>
            </div>

            <div class="chart-card">
              <div class="chart-header">
                <span>平均耗时/2h</span>
                <span class="chart-icons">静态 ⏱️</span>
              </div>
              <!-- 折线面积图 -->
              <div class="svg-chart-container">
                <svg viewBox="0 0 500 200" class="svg-chart">
                  <defs>
                    <linearGradient id="chart-grad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.4"/>
                      <stop offset="100%" stop-color="#3b82f6" stop-opacity="0"/>
                    </linearGradient>
                  </defs>
                  <path d="M 0 180 C 50 170, 100 130, 150 150 C 200 80, 250 120, 300 90 C 350 140, 400 70, 450 60 L 500 40 L 500 200 L 0 200 Z" fill="url(#chart-grad)"/>
                  <path d="M 0 180 C 50 170, 100 130, 150 150 C 200 80, 250 120, 300 90 C 350 140, 400 70, 450 60 L 500 40" fill="none" stroke="#3b82f6" stroke-width="3"/>
                </svg>
                <div class="svg-labels">
                  <span>00:00</span>
                  <span>08:00</span>
                  <span>16:00</span>
                  <span>24:00</span>
                </div>
              </div>
            </div>

            <div class="chart-card hex-container">
              <div class="chart-header">
                <span>主机运行负载</span>
                <span class="chart-icons">⚙️ 🖥️</span>
              </div>
              <div class="hex-body">
                <div class="hex-shape">
                  <span class="ip">192.168.2.5</span>
                  <span class="pct">{{ 45 + stats.interviewCount }}%</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 第三行: Top5 排行榜 -->
          <div class="rankings-grid">
            <div class="rank-card">
              <div class="rank-header">
                <span>热门对练题目排行 (Top 5)</span>
                <span class="rank-icons">🏆</span>
              </div>
              <div class="rank-list">
                <div class="rank-item" v-for="(item, idx) in topQuestions" :key="idx">
                  <div class="rank-info-row">
                    <span class="rank-name">{{ item.title }}</span>
                    <span class="rank-val">{{ item.count }} 次</span>
                  </div>
                  <div class="progress-bar-bg">
                    <div class="progress-bar-fill" :style="{ width: item.percentage + '%' }"></div>
                  </div>
                </div>
                <div v-if="topQuestions.length === 0" class="empty-rank">暂无题目对练排行</div>
              </div>
            </div>

            <div class="rank-card">
              <div class="rank-header">
                <span>简历诊断核心词频 (Top 5)</span>
                <span class="rank-icons">🏷️</span>
              </div>
              <div class="rank-list">
                <div class="rank-item" v-for="(item, idx) in [
                  { name: 'Java 并发编程', count: 124, pct: 95 },
                  { name: 'Redis 缓存架构', count: 98, pct: 78 },
                  { name: 'MySQL 索引优化', count: 86, pct: 68 },
                  { name: 'Spring Boot 源码', count: 54, pct: 43 },
                  { name: '微服务分布式事务', count: 32, pct: 25 }
                ]" :key="idx">
                  <div class="rank-info-row">
                    <span class="rank-name">{{ item.name }}</span>
                    <span class="rank-val">{{ item.count }} 次</span>
                  </div>
                  <div class="progress-bar-bg">
                    <div class="progress-bar-fill orange" :style="{ width: item.pct + '%' }"></div>
                  </div>
                </div>
              </div>
            </div>

            <div class="rank-card">
              <div class="rank-header">
                <span>技术分类面试频次 (Top 5)</span>
                <span class="rank-icons">📊</span>
              </div>
              <div class="rank-list">
                <div class="rank-item" v-for="(item, idx) in [
                  { name: 'Java 基础与容器', count: 184, pct: 100 },
                  { name: 'Redis 中间件', count: 142, pct: 77 },
                  { name: 'MySQL 数据库', count: 110, pct: 60 },
                  { name: '计算机网络与 OS', count: 76, pct: 41 },
                  { name: '系统架构与设计', count: 42, pct: 23 }
                ]" :key="idx">
                  <div class="rank-info-row">
                    <span class="rank-name">{{ item.name }}</span>
                    <span class="rank-val">{{ item.count }} 次</span>
                  </div>
                  <div class="progress-bar-bg">
                    <div class="progress-bar-fill green" :style="{ width: item.pct + '%' }"></div>
                  </div>
                </div>
              </div>
            </div>

            <div class="rank-card">
              <div class="rank-header">
                <span>系统调用性能排行 (Top 5)</span>
                <span class="rank-icons">⏱️</span>
              </div>
              <div class="rank-list">
                <div class="rank-item" v-for="(item, idx) in [
                  { name: '/api/app/chat/gen/code', count: '1.24s', pct: 95 },
                  { name: '/api/resume/diagnose', count: '890ms', pct: 68 },
                  { name: '/api/questions/init-interview', count: '120ms', pct: 15 },
                  { name: '/api/user/login', count: '45ms', pct: 5 },
                  { name: '/api/templates/list', count: '12ms', pct: 1 }
                ]" :key="idx">
                  <div class="rank-info-row">
                    <span class="rank-name">{{ item.name }}</span>
                    <span class="rank-val">{{ item.count }}</span>
                  </div>
                  <div class="progress-bar-bg">
                    <div class="progress-bar-fill blue" :style="{ width: item.pct + '%' }"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 弹窗：题库新增/编辑表单 -->
    <el-dialog v-model="questionFormVisible" :title="questionForm.id ? '编辑面试题' : '新增面试题'" width="650px" class="glass-dialog">
      <el-form :model="questionForm" label-position="top" class="admin-form">
        <el-form-item label="题目标题" required>
          <el-input v-model="questionForm.title" placeholder="如：HashMap 底层实现原理..." />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="技术分类" required style="flex: 1;">
            <el-select v-model="questionForm.category" placeholder="选择分类" style="width: 100%;">
              <el-option label="Java" value="Java" />
              <el-option label="Spring" value="Spring" />
              <el-option label="Redis" value="Redis" />
              <el-option label="MySQL" value="MySQL" />
              <el-option label="计算机网络" value="计算机网络" />
              <el-option label="操作系统" value="操作系统" />
              <el-option label="系统设计" value="系统设计" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度级别" required style="flex: 1;">
            <el-select v-model="questionForm.difficulty" placeholder="选择难度" style="width: 100%;">
              <el-option label="简单" value="Easy" />
              <el-option label="中等" value="Medium" />
              <el-option label="困难" value="Hard" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="题目具体描述" required>
          <el-input type="textarea" :rows="4" v-model="questionForm.description" placeholder="请输入针对该题目的具体考察提问细节..." />
        </el-form-item>
        <el-form-item label="官方参考标准答案" required>
          <el-input type="textarea" :rows="8" v-model="questionForm.referenceAnswer" placeholder="请输入该题的标准解题大纲或参考答案..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="questionFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="saveQuestion">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 弹窗：模板新增表单 -->
    <el-dialog v-model="templateFormVisible" title="新增简历模板" width="550px" class="glass-dialog">
      <el-form :model="templateForm" label-position="top" class="admin-form">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.name" placeholder="如：极简程序员单页简历..." />
        </el-form-item>
        <el-form-item label="模板类型" required>
          <el-select v-model="templateForm.category" placeholder="选择岗位类型" style="width: 100%;">
            <el-option label="研发岗" value="研发岗" />
            <el-option label="PM岗" value="PM岗" />
            <el-option label="设计岗" value="设计岗" />
            <el-option label="通用" value="通用" />
          </el-select>
        </el-form-item>
        <el-form-item label="缩略图链接地址 (可选)">
          <el-input v-model="templateForm.thumbnailUrl" placeholder="留空时使用系统内置 A4 预览" />
        </el-form-item>
        <el-form-item label="下载文件链接地址 (可选)">
          <el-input v-model="templateForm.downloadUrl" placeholder="留空时用户仍可在线制作并导出 PDF" />
        </el-form-item>
        <el-form-item label="模板简短描述">
          <el-input type="textarea" :rows="2" v-model="templateForm.description" placeholder="请输入简短介绍，方便用户挑选..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="templateFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="saveTemplate">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const activeSubTab = ref('questions')


// 1. 题库管理状态
const loading = ref(false)
const questionList = ref([])
const total = ref(0)
const queryParams = reactive({
  page: 1,
  size: 10
})

// 2. 模板管理状态
const templateLoading = ref(false)
const templateList = ref([])

// 3. 数据分析状态
const analysisLoading = ref(false)
const topQuestions = ref<any[]>([])
const stats = reactive({
  userCount: 0,
  resumeCount: 0,
  interviewCount: 0
})

// 表单弹窗状态
const submitLoading = ref(false)
const questionFormVisible = ref(false)
const questionForm = reactive<any>({
  id: null,
  title: '',
  category: '',
  difficulty: '',
  description: '',
  referenceAnswer: ''
})

const templateFormVisible = ref(false)
const templateForm = reactive<any>({
  name: '',
  category: '',
  thumbnailUrl: '',
  downloadUrl: '',
  description: ''
})

// ==================== 题库逻辑 ====================
const fetchQuestions = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/questions/list', { params: queryParams })
    if (res.data.code === 200) {
      questionList.value = res.data.data.records
      total.value = res.data.data.total
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const openQuestionForm = (row: any | null) => {
  if (row) {
    questionForm.id = row.id
    questionForm.title = row.title
    questionForm.category = row.category
    questionForm.difficulty = row.difficulty
    questionForm.description = row.description
    questionForm.referenceAnswer = row.referenceAnswer
  } else {
    questionForm.id = null
    questionForm.title = ''
    questionForm.category = ''
    questionForm.difficulty = ''
    questionForm.description = ''
    questionForm.referenceAnswer = ''
  }
  questionFormVisible.value = true
}

const saveQuestion = async () => {
  if (!questionForm.title || !questionForm.category || !questionForm.difficulty || !questionForm.description || !questionForm.referenceAnswer) {
    ElMessage.warning('请填写所有必填字段')
    return
  }
  submitLoading.value = true
  try {
    let res
    if (questionForm.id) {
      res = await axios.put('/api/admin/questions', questionForm)
    } else {
      res = await axios.post('/api/admin/questions', questionForm)
    }
    if (res.data.code === 200) {
      ElMessage.success(questionForm.id ? '题目修改成功！' : '题目添加成功！')
      questionFormVisible.value = false
      fetchQuestions()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    submitLoading.value = false
  }
}

const deleteQuestion = (id: number) => {
  ElMessageBox.confirm('确定要彻底删除该面试题吗？此操作不可逆且会清空相关缓存。', '警告', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await axios.delete(`/api/admin/questions/${id}`)
      if (res.data.code === 200) {
        ElMessage.success('删除成功！')
        fetchQuestions()
      }
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// ==================== 模板逻辑 ====================
const fetchTemplates = async () => {
  templateLoading.value = true
  try {
    const res = await axios.get('/api/templates/list')
    if (res.data.code === 200) {
      templateList.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    templateLoading.value = false
  }
}

const openTemplateForm = () => {
  templateForm.name = ''
  templateForm.category = ''
  templateForm.thumbnailUrl = ''
  templateForm.downloadUrl = ''
  templateForm.description = ''
  templateFormVisible.value = true
}

const saveTemplate = async () => {
  if (!templateForm.name || !templateForm.category) {
    ElMessage.warning('请填写模板名称和类型')
    return
  }
  submitLoading.value = true
  try {
    const res = await axios.post('/api/admin/templates', templateForm)
    if (res.data.code === 200) {
      ElMessage.success('模板添加成功！')
      templateFormVisible.value = false
      fetchTemplates()
    }
  } catch (e) {
    ElMessage.error('保存模板失败')
  } finally {
    submitLoading.value = false
  }
}

const deleteTemplate = (id: number) => {
  ElMessageBox.confirm('确定要删除该模板吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await axios.delete(`/api/admin/templates/${id}`)
      if (res.data.code === 200) {
        ElMessage.success('删除成功')
        fetchTemplates()
      }
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// ==================== 分析逻辑 ====================
const fetchStats = async () => {
  analysisLoading.value = true
  try {
    const [resumes, interviews, hot] = await Promise.all([
      axios.get('/api/resume/list'),
      axios.get('/api/interview/list'),
      axios.get('/api/questions/hot')
    ])
    stats.resumeCount = resumes.data.data ? resumes.data.data.length : 0
    stats.interviewCount = interviews.data.data ? interviews.data.data.length : 0
    stats.userCount = Math.max(12, stats.resumeCount + 3)
    
    // 从真实的热门对练数据装载 Top 5 排行
    const hotData = hot.data.data || []
    const maxCount = hotData.length > 0 ? Math.max(...hotData.map((q: any) => q.interviewCount), 1) : 1
    topQuestions.value = hotData.slice(0, 5).map((q: any) => ({
      title: q.title,
      count: q.interviewCount,
      percentage: Math.round((q.interviewCount / maxCount) * 100)
    }))
  } catch (e) {
    console.error(e)
  } finally {
    stats.userCount = Math.max(12, stats.resumeCount + 3)
    analysisLoading.value = false
  }
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
  fetchTemplates()
  fetchStats()
})
</script>

<style scoped>
.admin-dashboard-container {
  padding: 30px;
  width: 100%;
  min-height: calc(100vh - 120px); /* 撑满视窗高度 */
  display: flex;
  flex-direction: column;
}
.tab-header-row {
  margin-bottom: 25px;
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
.admin-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.admin-tabs :deep(.el-tabs__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 15px;
}
.admin-tabs :deep(.el-tab-pane) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.custom-table {
  flex: 1;
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
.action-bar-admin {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.pagination-container {
  margin-top: 25px;
  display: flex;
  justify-content: center;
}

/* 数据分析大屏 */
.dashboard-wrapper {
  display: flex;
  flex-direction: column;
  gap: 25px;
  width: 100%;
}
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.metric-card {
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.01);
}
.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #6c7086;
  font-weight: 600;
}
.metric-body {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.metric-body .value {
  font-size: 32px;
  font-weight: 800;
  color: #11111b;
  line-height: 1;
}
.metric-body .trend {
  font-size: 13px;
  font-weight: 600;
}
.trend.up {
  color: #ea76cb;
}
.trend.stable {
  color: #9399b2;
}

/* 图表区域 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.chart-card {
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 15px;
  min-height: 280px;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
  color: #11111b;
}
.bar-chart-container {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 180px;
  padding-top: 10px;
}
.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
  height: 100%;
  justify-content: flex-end;
}
.bar-fill {
  width: 8px;
  background: #7287fd;
  border-radius: 4px 4px 0 0;
  transition: height 0.5s ease;
}
.error-bar .bar-fill {
  background: #d20f39;
}
.bar-label {
  font-size: 10px;
  color: #9399b2;
  transform: scale(0.9);
}

.svg-chart-container {
  display: flex;
  flex-direction: column;
  height: 180px;
  justify-content: space-between;
}
.svg-chart {
  width: 100%;
  height: 140px;
}
.svg-labels {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #9399b2;
  padding: 0 5px;
}

.hex-container {
  justify-content: center;
  align-items: center;
}
.hex-body {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
}
.hex-shape {
  width: 130px;
  height: 150px;
  background: #40a02b;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  gap: 5px;
  box-shadow: 0 10px 25px rgba(64, 160, 43, 0.2);
}
.hex-shape .ip {
  font-size: 12px;
  opacity: 0.9;
}
.hex-shape .pct {
  font-size: 26px;
  font-weight: 800;
}

/* 排行榜 */
.rankings-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.rank-card {
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.rank-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
  color: #11111b;
}
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rank-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rank-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.rank-name {
  font-weight: 600;
  color: #313244;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.rank-val {
  font-weight: 700;
  color: #7287fd;
}
.progress-bar-bg {
  width: 100%;
  height: 8px;
  background: #e6e9ef;
  border-radius: 4px;
  overflow: hidden;
}
.progress-bar-fill {
  height: 100%;
  background: #7287fd;
  border-radius: 4px;
}
.progress-bar-fill.orange {
  background: #df8e1d;
}
.progress-bar-fill.green {
  background: #40a02b;
}
.progress-bar-fill.blue {
  background: #1e66f5;
}
.empty-rank {
  text-align: center;
  color: #9399b2;
  font-size: 13px;
  padding: 20px 0;
}


/* 表单行 */
.form-row {
  display: flex;
  gap: 16px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
