<template>
  <div class="app-container">
    <!-- 主门户界面 (对游客和登录用户均展示) -->
    <div class="portal-wrapper">

      <!-- 左侧现代化侧边栏 -->
      <aside class="sidebar">
        <div class="sidebar-header">
          <div class="logo-icon">AI</div>
          <div class="title-box">
            <h3>面试教练</h3>
            <span class="version-tag">Beta v1.1</span>
          </div>
        </div>

        <nav class="sidebar-menu">
          <button 
            v-for="tab in visibleTabs" 
            :key="tab.value" 
            :class="['menu-item', { active: activeTab === tab.value }]" 
            @click="switchTab(tab.value)"
          >
            <el-icon><component :is="tab.icon" /></el-icon>
            <span>{{ tab.label }}</span>
          </button>
        </nav>


        <!-- 每日签到与 ⚡ AI 算力能量槽 -->
        <div class="sidebar-energy-card" v-if="token">
          <div class="energy-header">
            <span class="energy-title">⚡ AI 算力能量槽</span>
            <el-tooltip content="算力能量每 5 小时自动充满。签到可额外获得 +200 EP 上限！" placement="top">
              <el-icon class="info-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          
          <div class="progress-container">
            <el-progress 
              :percentage="energyPercentage" 
              :stroke-width="8" 
              :status="energyPercentage < 20 ? 'exception' : ''"
              :color="energyColor"
              :show-text="false"
            />
            <div class="energy-text">
              <span class="energy-val">{{ availableEnergy }}</span>
              <span class="energy-max">/ {{ maxEnergy }} EP</span>
            </div>
          </div>

          <button 
            :disabled="hasSignedToday" 
            :class="['sign-btn', { 'signed': hasSignedToday }]"
            @click="handleSign"
          >
            <el-icon><Calendar /></el-icon>
            <span>{{ hasSignedToday ? '今日已签到' : '签到领 +200 EP 额度' }}</span>
          </button>
          
          <div class="sign-days-tip" v-if="continuousSignDays > 0">
            已连续签到 <span class="highlight">{{ continuousSignDays }}</span> 天
          </div>
        </div>

        <div class="sidebar-footer">
          <div class="user-info" v-if="token">
            <el-avatar :size="32" class="user-avatar">{{ username.substring(0, 1).toUpperCase() }}</el-avatar>
            <div class="user-detail">
              <span class="user-name">{{ username }}</span>
              <span class="user-role">{{ userRole === 'admin' ? '管理员' : '普通用户' }}</span>
            </div>
          </div>
          <button class="logout-btn" @click="handleLogout" title="退出登录" v-if="token">
            <el-icon><SwitchButton /></el-icon>
          </button>
          
          <!-- 游客登录入口 -->
          <button class="login-trigger-btn" @click="loginDialogVisible = true" v-else>
            <el-icon><User /></el-icon>
            <span>登录 / 注册</span>
          </button>
        </div>

      </aside>

      <!-- 右侧内容视窗 -->
      <main class="content-viewport">
        <!-- TAB 1: 智能工作台 -->
        <div v-if="activeTab === 'workspace'" :class="['tab-content', { 'center-layout': !currentInterviewId && !showReport && !resumeDiagnosis }]">
          <!-- 上传简历与状态检查界面 -->
          <el-card class="glass-card main-card" v-if="!currentInterviewId && !showReport">
            <template #header>
              <div class="card-header">
                <h2>智能诊断与面试工作台</h2>
                <span class="subtitle">上传简历快速获取诊断，点击即可启动个性化 AI 模拟面试</span>
              </div>
            </template>

            <!-- 拖拽上传区域 (包含前置登录拦截) -->
            <el-upload
              class="upload-area"
              drag
              action="/api/resume/upload"
              :headers="authHeaders"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :before-upload="handleBeforeUpload"
              accept=".pdf"
            >

              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                拖拽简历 (PDF) 到此处，或 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  请上传标准文本 PDF 简历（暂不支持图片扫描版），文件大小不超过 10MB
                </div>
              </template>
            </el-upload>

            <!-- 上传成功后的诊断轮询与报告显示 -->
            <div v-if="resumeId" class="status-section">
              <div class="action-bar" v-if="!resumeDiagnosis">
                <el-button type="primary" :loading="checkingDiagnosis" @click="checkDiagnosisStatus" size="large">
                  检查诊断进度
                </el-button>
              </div>

              <!-- 简历诊断详细展示 -->
              <div v-if="resumeDiagnosis" class="diagnosis-panel">
                <h3 class="panel-title"><el-icon><Document /></el-icon> 简历诊断结果</h3>
                
                <div class="diagnosis-grid">
                  <div class="diag-card advantages">
                    <h4><span class="dot green"></span> 竞争优势</h4>
                    <ul>
                      <li v-for="(item, i) in resumeDiagnosis.advantages" :key="i">{{ item }}</li>
                    </ul>
                  </div>

                  <div class="diag-card disadvantages">
                    <h4><span class="dot orange"></span> 改进空间</h4>
                    <ul>
                      <li v-for="(item, i) in resumeDiagnosis.disadvantages" :key="i">{{ item }}</li>
                    </ul>
                  </div>
                </div>

                <div class="diag-card suggestions">
                  <h4><span class="dot blue"></span> 针对性优化建议</h4>
                  <ul>
                    <li v-for="(item, i) in resumeDiagnosis.suggestions" :key="i">{{ item }}</li>
                  </ul>
                </div>

                <div class="interview-trigger">
                  <el-button type="success" size="large" class="start-btn" @click="startInterview(resumeId)">
                    基于此简历开启 AI 模拟面试
                  </el-button>
                </div>
              </div>
            </div>
          </el-card>
   <!-- 模拟面试对话界面 -->
          <el-card class="glass-card chat-card" v-else-if="currentInterviewId && !showReport">
            <template #header>
              <div class="card-header chat-header">
                <div class="interviewer-info">
                  <span class="status-dot pulsing"></span>
                  <div>
                    <h3>AI 资深后端面试官</h3>
                    <span class="desc">根据简历向你发起深度技术提问</span>
                    <span v-if="agentStreamStatus" class="desc">{{ agentStreamStatus }}</span>
                  </div>
                </div>
                <el-button type="danger" plain size="small" @click="confirmEndInterview">结束面试并评估</el-button>
              </div>
            </template>
            
            <div class="chat-history" ref="chatBox">
              <div v-for="(msg, index) in messages" :key="index" :class="['message-wrapper', msg.role]">
                <el-avatar :size="36" class="chat-avatar" v-if="msg.role === 'assistant'">AI</el-avatar>
                <div class="message-bubble">
                  <div class="msg-content">{{ msg.content }}</div>
                </div>
                <el-avatar :size="36" class="chat-avatar" v-if="msg.role === 'user'">{{ username.substring(0, 1).toUpperCase() }}</el-avatar>
              </div>
              <div v-if="sending" class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>

            <div class="chat-input-bar">
              <el-input 
                v-model="inputText" 
                placeholder="在此输入你的回答，按 Enter 键发送..." 
                @keyup.enter="sendMessage" 
                :disabled="sending"
                size="large"
              ></el-input>
              <el-button type="primary" @click="sendMessage" :loading="sending" size="large">发送</el-button>
            </div>
          </el-card>

          <!-- 面试结束后展示报告界面 -->
          <el-card class="glass-card report-card" v-else-if="showReport && lastReport">
            <template #header>
              <div class="card-header report-header">
                <h2>模拟面试评估报告</h2>
                <el-button type="primary" @click="backToWorkspace" size="small">返回工作台</el-button>
              </div>
            </template>

            <div class="report-container">
              <div class="score-section">
                <div class="score-circle-wrapper">
                  <div class="score-circle" :style="getScoreColorStyle(lastReport.score)">
                    <span class="score-num">{{ lastReport.score }}</span>
                    <span class="score-label">综合得分</span>
                  </div>
                </div>
                <div class="score-comment">
                  <h3>面试评价摘要</h3>
                  <p class="summary-text">{{ lastReport.evaluation }}</p>
                </div>
              </div>

              <div class="report-details" style="margin-top: 30px;">
                <h3>面谈历史回顾</h3>
                <div class="history-chat-list">
                  <div v-for="(msg, index) in parsedChatHistory" :key="index" :class="['history-msg-item', msg.role]">
                    <span class="role-badge">{{ msg.role === 'user' ? '我的回答' : '面试官提问' }}</span>
                    <p class="msg-text">{{ msg.content }}</p>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- TAB 2: 我的简历诊断历史 -->
        <div v-else-if="activeTab === 'resumes'" class="tab-content">
          <div class="glass-card list-card-container">
            <div class="tab-header-row">
              <h2>简历诊断历史</h2>
              <span class="subtitle">记录了您以往上传诊断的简历数据</span>
            </div>

            <el-table :data="resumeList" style="width: 100%;" class="custom-table" v-loading="loadingList">
              <el-table-column prop="fileName" label="文件名" min-width="400"></el-table-column>
              <el-table-column prop="createTime" label="上传时间" min-width="240">
                <template #default="scope">
                  {{ formatDate(scope.row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" min-width="160">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 1 ? 'success' : (scope.row.status === 2 ? 'danger' : 'info')">
                    {{ scope.row.status === 1 ? '已诊断' : (scope.row.status === 2 ? '失败' : '处理中') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" min-width="220" fixed="right">
                <template #default="scope">
                  <el-button 
                    type="primary" 
                    size="small" 
                    :disabled="scope.row.status !== 1" 
                    @click="viewHistoricResume(scope.row)"
                  >查看诊断</el-button>
                  <el-button 
                    type="success" 
                    size="small" 
                    :disabled="scope.row.status !== 1" 
                    @click="startInterviewFromHistory(scope.row.id)"
                  >发起面试</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- TAB 3: 我的模拟面试历史 -->
        <div v-else-if="activeTab === 'interviews'" class="tab-content">
          <div class="glass-card list-card-container">
            <div class="tab-header-row">
              <h2>模拟面试记录</h2>
              <span class="subtitle">记录了您已结束的全部 AI 模拟面试及得分报告</span>
            </div>

            <el-table :data="interviewList" style="width: 100%;" class="custom-table" v-loading="loadingList">
              <el-table-column prop="id" label="面试编号" min-width="120"></el-table-column>
              <el-table-column prop="score" label="综合得分" min-width="160">
                <template #default="scope">
                  <el-tag :type="scope.row.score >= 80 ? 'success' : (scope.row.score >= 60 ? 'warning' : 'danger')" v-if="scope.row.status === 1">
                    {{ scope.row.score }} 分
                  </el-tag>
                  <el-tag type="info" v-else>未完结</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" min-width="160">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 1 ? 'info' : 'success'">
                    {{ scope.row.status === 1 ? '已结束' : '进行中' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" min-width="260">
                <template #default="scope">
                  {{ formatDate(scope.row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" min-width="180" fixed="right">
                <template #default="scope">
                  <el-button 
                    v-if="scope.row.status === 1"
                    type="primary" 
                    size="small" 
                    @click="viewHistoricInterview(scope.row.id)"
                  >查看评估</el-button>
                  <el-button
                    v-else
                    type="success"
                    size="small"
                    @click="continueInterview(scope.row.id)"
                  >继续面试</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- TAB 4: 经典题库 -->
        <div v-else-if="activeTab === 'questions'" class="tab-content">
          <QuestionBank @start-interview="handleStartInterviewFromQuestion" />
        </div>

        <!-- TAB 5: 模板广场 -->
        <div v-else-if="activeTab === 'templates'" class="tab-content">
          <TemplateSquare />
        </div>

        <!-- TAB 6: 管理后台 -->
        <div v-else-if="activeTab === 'admin'" class="tab-content">
          <AdminDashboard />
        </div>
      </main>

    </div>

    <!-- 弹窗：查看历史简历诊断报告 -->
    <el-dialog v-model="diagDialogVisible" title="简历历史诊断结果" width="1300px" class="glass-dialog">
      <div v-if="selectedResumeDiag" class="diagnosis-panel dialog-diag">
        <div class="diag-card advantages">
          <h4><span class="dot green"></span> 竞争优势</h4>
          <ul>
            <li v-for="(item, i) in selectedResumeDiag.advantages" :key="i">{{ item }}</li>
          </ul>
        </div>
        <div class="diag-card disadvantages">
          <h4><span class="dot orange"></span> 改进空间</h4>
          <ul>
            <li v-for="(item, i) in selectedResumeDiag.disadvantages" :key="i">{{ item }}</li>
          </ul>
        </div>
        <div class="diag-card suggestions">
          <h4><span class="dot blue"></span> 针对性优化建议</h4>
          <ul>
            <li v-for="(item, i) in selectedResumeDiag.suggestions" :key="i">{{ item }}</li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <!-- 弹窗：查看历史面试评估报告与对话历史 -->
    <el-dialog v-model="interviewDialogVisible" title="面试历史诊断评估" width="1450px" class="glass-dialog">
      <div v-if="selectedInterview" class="historic-interview-panel">
        <div class="score-section compact-score">
          <div class="score-circle-wrapper">
            <div class="score-circle" :style="getScoreColorStyle(selectedInterview.score)">
              <span class="score-num">{{ selectedInterview.score }}</span>
              <span class="score-label">最终得分</span>
            </div>
          </div>
          <div class="score-comment">
            <h3>面试评价摘要</h3>
            <p class="summary-text">{{ selectedInterview.evaluation }}</p>
          </div>
        </div>

        <h3 class="history-title">面试提问与回答回放</h3>
        <div class="history-chat-list dialog-chat-list">
          <div v-for="(msg, index) in getParsedHistory(selectedInterview.chatHistory)" :key="index" :class="['history-msg-item', msg.role]">
            <span class="role-badge">{{ msg.role === 'user' ? '我的回答' : '面试官提问' }}</span>
            <p class="msg-text">{{ msg.content }}</p>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 弹窗：登录 / 注册对话框 (针对游客操作的拦截弹窗) -->
    <el-dialog v-model="loginDialogVisible" width="600px" class="glass-dialog login-dialog-modal" :show-close="true">
      <div class="login-card-inside">
        <div class="login-header">
          <div class="logo-circle">
            <el-icon><ChatLineRound /></el-icon>
          </div>
          <h2>AI 面试教练门户</h2>
          <p>{{ isRegister ? '创建您的专属 AI 备考账号' : '登录系统开启智能诊断与模拟面试' }}</p>
        </div>

        <el-form :model="authForm" label-position="top" class="auth-form" @keyup.enter="handleAuth">
          <el-form-item label="用户名">
            <el-input v-model="authForm.username" placeholder="请输入用户名" :prefix-icon="User"></el-input>
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="authForm.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock"></el-input>
          </el-form-item>

          <div style="margin-top: 20px;">
            <el-button type="primary" class="auth-btn" :loading="authLoading" @click="handleAuth">
              {{ isRegister ? '注 册' : '登 录' }}
            </el-button>
          </div>
        </el-form>

        <div class="auth-footer">
          <span>{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
          <el-link type="primary" @click="toggleAuthMode">{{ isRegister ? '立即登录' : '立即注册' }}</el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import { 
  UploadFilled, Document, ChatLineRound, User, Lock, SwitchButton, Trophy, List, QuestionFilled, Calendar 
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

// 导入新组件
import QuestionBank from './components/QuestionBank.vue'
import TemplateSquare from './components/TemplateSquare.vue'
import AdminDashboard from './components/AdminDashboard.vue'

// 1. 用户鉴权状态与 Token 配置
const token = ref<string | null>(localStorage.getItem('token'))
const username = ref<string>(localStorage.getItem('username') || '')
const userRole = ref<string>(localStorage.getItem('userRole') || 'user')
const loginDialogVisible = ref(false)



const authHeaders = computed(() => {
  return token.value ? { 'authorization': token.value } : {}
})


// 1.1 算力能量槽与签到相关状态
const hasSignedToday = ref(false)
const continuousSignDays = ref(0)
const usedEnergy = ref(0)
const maxEnergy = ref(1000)

const availableEnergy = computed(() => {
  const diff = maxEnergy.value - usedEnergy.value
  return diff > 0 ? diff : 0
})

const energyPercentage = computed(() => {
  if (maxEnergy.value <= 0) return 0
  const pct = (availableEnergy.value / maxEnergy.value) * 100
  return Math.round(pct)
})

const energyColor = computed(() => {
  const pct = energyPercentage.value
  if (pct >= 50) return '#67C23A' // 绿色
  if (pct >= 20) return '#E6A23C' // 橙色
  return '#F56C6C' // 红色
})

const fetchSignStatus = async () => {
  if (!token.value) return
  try {
    const res = await axios.get('/api/user/sign/status')
    if (res.data.code === 200) {
      const data = res.data.data
      hasSignedToday.value = data.hasSignedToday
      continuousSignDays.value = data.continuousSignDays
      usedEnergy.value = data.usedEnergy
      maxEnergy.value = data.maxEnergy
    }
  } catch (e) {
    console.error('获取签到与算力状态失败:', e)
  }
}

const handleSign = async () => {
  if (hasSignedToday.value) return
  try {
    const res = await axios.post('/api/user/sign')
    if (res.data.code === 200) {
      ElMessage.success(res.data.data || '签到成功！已为您提升能量上限')
      fetchSignStatus()
    } else {
      ElMessage.error(res.data.message || '签到失败')
    }
  } catch(e) {
    ElMessage.error('签到接口请求失败')
  }
}

// 初始化获取一次签到状态
if (token.value) {
  fetchSignStatus()
}


// Axios 全局拦截器配置
axios.interceptors.request.use(config => {
  if (token.value) {
    config.headers['authorization'] = token.value
  }
  return config
}, error => {
  return Promise.reject(error)
})

axios.interceptors.response.use(response => {
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    ElMessage.error('登录会话已失效，请重新登录')
    handleLogout()
  }
  return Promise.reject(error)
})

// 2. 登录与注册模块
const isRegister = ref(false)
const authLoading = ref(false)
const authForm = reactive({
  username: '',
  password: ''
})

const toggleAuthMode = () => {
  isRegister.value = !isRegister.value
  authForm.username = ''
  authForm.password = ''
}

const handleAuth = async () => {
  if (!authForm.username.trim() || !authForm.password.trim()) {
    ElMessage.warning('用户名或密码不能为空')
    return
  }
  authLoading.value = true
  try {
    if (isRegister.value) {
      const res = await axios.post('/api/user/register', authForm)
      if (res.data.code === 200) {
        ElMessage.success('注册成功！已切换到登录')
        isRegister.value = false
        authForm.password = ''
      } else {
        ElMessage.error(res.data.message || '注册失败')
      }
    } else {
      const res = await axios.post('/api/user/login', authForm)
      if (res.data.code === 200) {
        const generatedToken = res.data.data
        token.value = generatedToken
        username.value = authForm.username
        localStorage.setItem('token', generatedToken)
        localStorage.setItem('username', authForm.username)
        ElMessage.success('登录成功！欢迎进入面试教练系统')
        await fetchUserInfo() // 登录成功获取用户角色信息
        fetchSignStatus() // 登录成功获取能量槽状态
        loginDialogVisible.value = false // 关闭登录弹窗
        switchTab('workspace')
      } else {
        ElMessage.error(res.data.message || '用户名或密码错误')
      }
    }


  } catch (e) {
    ElMessage.error('网络或服务器连接失败')
  } finally {
    authLoading.value = false
  }
}

const handleLogout = async () => {
  try {
    if (token.value) {
      await axios.post('/api/user/logout')
    }
  } catch(e) {}
  token.value = null
  username.value = ''
  userRole.value = 'user'
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('userRole')
  // Reset states
  resumeId.value = null
  resumeDiagnosis.value = null
  currentInterviewId.value = null
  showReport.value = false
}


// 3. 门户页面 Tab 控制
const activeTab = ref('workspace')

const visibleTabs = computed(() => {
  const baseTabs = [
    { value: 'workspace', label: '智能工作台', icon: Trophy },
    { value: 'questions', label: '经典题库', icon: QuestionFilled },
    { value: 'templates', label: '模板广场', icon: Document }
  ]
  if (token.value) {
    baseTabs.push(
      { value: 'resumes', label: '简历诊断历史', icon: Document },
      { value: 'interviews', label: '模拟面试历史', icon: List }
    )
    if (userRole.value === 'admin') {
      baseTabs.push({ value: 'admin', label: '⚙️ 管理后台', icon: Lock })
    }
  }
  return baseTabs
})


const switchTab = (tabValue: string) => {
  activeTab.value = tabValue
  fetchSignStatus() // 切换 Tab 时刷新算力能量槽
  if (tabValue === 'resumes') {
    fetchResumes()
  } else if (tabValue === 'interviews') {
    fetchInterviews()
  }
}

const fetchUserInfo = async () => {
  if (!token.value) return
  try {
    const res = await axios.get('/api/user/me')
    if (res.data.code === 200) {
      userRole.value = res.data.data.role || 'user'
      localStorage.setItem('userRole', userRole.value)
    }
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }
}

// 初始化获取一次签到状态与角色
if (token.value) {
  fetchSignStatus()
  fetchUserInfo()
}

// 针对题目发起对练的处理器
const handleStartInterviewFromQuestion = async (qId: number) => {
  try {
    const res = await axios.get('/api/resume/list')
    if (res.data.code === 200) {
      const list = res.data.data
      if (!list || list.length === 0) {
        ElMessage.warning('您还没有上传过简历，请先在工作台上传一份简历。')
        switchTab('workspace')
        return
      }
      // 默认使用最近上传的一份简历（第一项）
      const rId = list[0].id
      
      // 调用初始化题目对练接口
      const initRes = await axios.post(`/api/questions/init-interview?questionId=${qId}&resumeId=${rId}`)
      fetchSignStatus()
      if (initRes.data.code === 200) {
        currentInterviewId.value = initRes.data.data
        messages.value = [{ role: 'assistant', content: '你好！我是你的 AI 面试官。我已经针对你选择的题目做好了准备，请问你准备好开始面试了吗？' }]
        showReport.value = false
        lastReport.value = null
        switchTab('workspace')
        scrollToBottom()
      } else {
        ElMessage.error(initRes.data.message || '面试初始化失败')
      }
    }
  } catch (e) {
    ElMessage.error('开启题目面试失败，请检查配置')
  }
}


// 4. 工作台 - 简历诊断业务
const resumeId = ref<number | null>(null)
const checkingDiagnosis = ref(false)
const resumeDiagnosis = ref<any>(null)

const handleUploadSuccess = (response: any) => {
  fetchSignStatus() // 上传响应后刷新能量槽（防诊断扣减延迟）
  if (response.code === 200) {
    ElMessage.success('简历上传成功！大模型诊断排队中...')
    resumeId.value = response.data
    resumeDiagnosis.value = null
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('上传失败，请检查网络或后端配置')
}

// 拖拽上传前的登录拦截钩子
const handleBeforeUpload = () => {
  if (!token.value) {
    loginDialogVisible.value = true
    ElMessage.warning('请先登录以使用简历诊断功能')
    return false
  }
  return true
}


const checkDiagnosisStatus = async () => {
  if (!resumeId.value) return
  checkingDiagnosis.value = true
  try {
    const res = await axios.get(`/api/resume/${resumeId.value}`)
    if (res.data.code === 200) {
      const data = res.data.data
      if (data.status === 0) {
        ElMessage.info('AI 面试官仍在解析和诊断中，请稍后再试...')
      } else if (data.status === 1) {
        ElMessage.success('诊断成功！')
        fetchSignStatus() // 诊断成功，刷新能量槽显示诊断扣减
        try {
          resumeDiagnosis.value = JSON.parse(data.diagnosisResult)
        } catch(e) {
          resumeDiagnosis.value = {
            advantages: ['解析格式略有偏差，请参考原始诊断'],
            disadvantages: ['详细文本内容如下'],
            suggestions: [data.diagnosisResult]
          }
        }
      } else {
        ElMessage.error('诊断失败，请检查简历PDF格式或后端日志')
      }
    }
  } catch (e) {
    ElMessage.error('检查进度请求失败')
  } finally {
    checkingDiagnosis.value = false
  }
}

// 5. 工作台 - 模拟面试业务
const currentInterviewId = ref<number | null>(null)
const messages = ref<{role: string, content: string}[]>([])
const inputText = ref('')
const sending = ref(false)
const agentStreamStatus = ref('')
const chatBox = ref<any>(null)

// 结束面试状态展示
const showReport = ref(false)
const lastReport = ref<any>(null)

const startInterview = async (rId: number) => {
  if (!token.value) {
    loginDialogVisible.value = true
    ElMessage.warning('请先登录以开启模拟面试')
    return
  }
  try {
    const res = await axios.post(`/api/interview/init?resumeId=${rId}`)
    fetchSignStatus() // 初始化请求后刷新能量槽
    if (res.data.code === 200) {
      currentInterviewId.value = res.data.data
      messages.value = [{ role: 'assistant', content: '你好！我是你的 AI 面试官。我已经阅读了你的简历，请问你准备好开始面试了吗？' }]
      showReport.value = false
      lastReport.value = null
      scrollToBottom()
    } else {
      ElMessage.error(res.data.message || '面试初始化失败')
    }
  } catch (e) {
    ElMessage.error('服务器故障，面试初始化失败')
  }
}


const scrollToBottom = () => {
  nextTick(() => {
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight
    }
  })
}

const sendMessage = () => {
  if (!inputText.value.trim() || sending.value || !currentInterviewId.value) return
  const userMsg = inputText.value
  messages.value.push({ role: 'user', content: userMsg })
  inputText.value = ''
  sending.value = true

  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  scrollToBottom()

  // Native SSE EventSource (EventSource supports query parameter authorization)
  const tokenQuery = token.value ? `&token=${token.value}` : ''
  const sse = new EventSource(`/api/interview/chat?interviewId=${currentInterviewId.value}&message=${encodeURIComponent(userMsg)}${tokenQuery}`)
  
  sse.onmessage = (event) => {
    let payload: any = null
    try {
      payload = JSON.parse(event.data)
    } catch (_) {
      payload = null
    }

    if (payload?.type === 'token') {
      agentStreamStatus.value = ''
      messages.value[aiMsgIndex].content += payload.content || ''
      scrollToBottom()
      return
    }
    if (payload?.type === 'tool_start') {
      const toolLabels: Record<string, string> = {
        get_resume_summary: '正在读取简历',
        get_user_weak_skills: '正在分析历史薄弱项',
        search_question_bank: '正在检索相关题目'
      }
      agentStreamStatus.value = toolLabels[payload.tool] || '正在查询面试资料'
      return
    }
    if (payload?.type === 'tool_done') {
      agentStreamStatus.value = '正在组织下一道问题'
      return
    }
    if (payload?.type === 'state') {
      agentStreamStatus.value = ''
      return
    }
    if (payload?.type === 'done') {
      agentStreamStatus.value = ''
      sse.close()
      sending.value = false
      fetchSignStatus()
      return
    }
    if (payload?.type === 'error') {
      console.error('AI stream error:', payload.message)
      agentStreamStatus.value = ''
      messages.value[aiMsgIndex].content = 'AI 服务调用失败，请检查 Python agent-service 和后端控制台日志。'
      sse.close()
      sending.value = false
      scrollToBottom()
      return
    }
    if (event.data?.startsWith('[AI_ERROR]')) {
      console.error('AI stream error:', event.data)
      messages.value[aiMsgIndex].content = 'AI 服务调用失败，请检查 Python agent-service 和后端控制台日志。'
      agentStreamStatus.value = ''
      sse.close()
      sending.value = false
      scrollToBottom()
      return
    }
    messages.value[aiMsgIndex].content += event.data
    scrollToBottom()
  }
  
  sse.onerror = (err) => {
    console.error('SSE connection error or closed:', err)
    agentStreamStatus.value = ''
    sse.close()
    sending.value = false
    fetchSignStatus() // 流式聊天结束，刷新已扣减的能量槽！
  }
}

const confirmEndInterview = () => {
  ElMessageBox.confirm(
    '您确定现在结束模拟面试吗？系统会根据您刚才的所有技术问答进行打分评估。',
    '提示',
    {
      confirmButtonText: '确定结束',
      cancelButtonText: '继续面试',
      type: 'warning'
    }
  ).then(async () => {
    if (!currentInterviewId.value) return
    sending.value = true
    try {
      const res = await axios.post(`/api/interview/end?interviewId=${currentInterviewId.value}`)
      fetchSignStatus() // 评估完成后刷新能量槽
      if (res.data.code === 200) {
        lastReport.value = res.data.data
        showReport.value = true
        currentInterviewId.value = null // 关闭聊天界面
        ElMessage.success('面试分析完成，报告已生成！')
      } else {
        ElMessage.error(res.data.message || '评估生成失败')
      }
    } catch(e) {
      ElMessage.error('服务器连接异常，评估生成失败')
    } finally {
      sending.value = false
    }
  }).catch(() => {})
}

const backToWorkspace = () => {
  showReport.value = false
  lastReport.value = null
  resumeId.value = null
  resumeDiagnosis.value = null
}

const parsedChatHistory = computed(() => {
  if (!lastReport.value || !lastReport.value.chatHistory) return []
  try {
    // Parser list message JSON array
    const list = JSON.parse(lastReport.value.chatHistory)
    // Ignore system prompts
    return list.filter((m: any) => m.role !== 'system')
  } catch(e) {
    return []
  }
})

// 6. 简历诊断历史 TAB 业务
const resumeList = ref([])
const loadingList = ref(false)
const diagDialogVisible = ref(false)
const selectedResumeDiag = ref<any>(null)

const fetchResumes = async () => {
  loadingList.value = true
  try {
    const res = await axios.get('/api/resume/list')
    if (res.data.code === 200) {
      resumeList.value = res.data.data
    }
  } catch(e) {
    ElMessage.error('获取简历历史失败')
  } finally {
    loadingList.value = false
  }
}

const viewHistoricResume = (row: any) => {
  try {
    selectedResumeDiag.value = JSON.parse(row.diagnosisResult)
    diagDialogVisible.value = true
  } catch(e) {
    selectedResumeDiag.value = {
      advantages: ['报告读取失败'],
      disadvantages: ['详细文本内容如下'],
      suggestions: [row.diagnosisResult]
    }
    diagDialogVisible.value = true
  }
}

const startInterviewFromHistory = (rId: number) => {
  resumeId.value = rId
  switchTab('workspace')
  startInterview(rId)
}

// 7. 模拟面试历史 TAB 业务
const interviewList = ref([])
const interviewDialogVisible = ref(false)
const selectedInterview = ref<any>(null)

const fetchInterviews = async () => {
  loadingList.value = true
  try {
    const res = await axios.get('/api/interview/list')
    if (res.data.code === 200) {
      interviewList.value = res.data.data
    }
  } catch(e) {
    ElMessage.error('获取面试历史失败')
  } finally {
    loadingList.value = false
  }
}

const viewHistoricInterview = async (id: number) => {
  try {
    const res = await axios.get(`/api/interview/${id}`)
    if (res.data.code === 200) {
      selectedInterview.value = res.data.data
      interviewDialogVisible.value = true
    }
  } catch(e) {
    ElMessage.error('获取面试报告失败')
  }
}

const continueInterview = async (id: number) => {
  try {
    const res = await axios.get(`/api/interview/${id}/resume`)
    if (res.data.code !== 200) {
      ElMessage.warning(res.data.message || '该面试已无法继续')
      return
    }

    const data = res.data.data
    currentInterviewId.value = data.interviewId
    resumeId.value = data.resumeId
    messages.value = Array.isArray(data.messages)
      ? data.messages.map((message: any) => ({
          role: message.role,
          content: typeof message.content === 'string'
            ? message.content.replace(/\\n/g, '\n')
            : String(message.content ?? '')
        }))
      : []
    showReport.value = false
    lastReport.value = null
    inputText.value = ''
    switchTab('workspace')
    scrollToBottom()
    ElMessage.success('已恢复上次面试，可以继续作答')
  } catch(e) {
    ElMessage.error('恢复面试失败，请检查 Java 与 Python Agent 服务')
  }
}

const getParsedHistory = (historyJsonStr: string) => {
  try {
    const list = JSON.parse(historyJsonStr)
    return list.filter((m: any) => m.role !== 'system')
  } catch(e) {
    return []
  }
}

// 8. 辅助函数
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString()
}

const getScoreColorStyle = (score: number) => {
  const color = score >= 80 ? '#67C23A' : (score >= 60 ? '#E6A23C' : '#F56C6C')
  return {
    border: `8px solid ${color}`,
    color: color
  }
}
</script>

<style>
/* 全局样式清除与底色 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  background: radial-gradient(circle at 10% 20%, rgba(216, 241, 230, 0.46) 0.1%, rgba(233, 226, 226, 0.28) 90.1%);
  min-height: 100vh;
  color: #333;
}
</style>

<style scoped>
/* 容器布局 */
.app-container {
  min-height: 100vh;
  display: flex;
}

/* 玻璃质感通用设计 */
.glass-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.45) !important;
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.08) !important;
  border-radius: 16px !important;
}

/* 1. 登录注册页面 */
.login-wrapper {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%);
  padding: 20px;
}
.login-card {
  width: 420px;
  padding: 35px 25px;
}
.login-header {
  text-align: center;
  margin-bottom: 30px;
}
.logo-circle {
  width: 72px;
  height: 72px;
  border-radius: 36px;
  background: linear-gradient(135deg, #aa3bff 0%, #6b3bff 100%);
  display: inline-flex;
  justify-content: center;
  align-items: center;
  font-size: 32px;
  color: white;
  margin-bottom: 15px;
  box-shadow: 0 4px 15px rgba(107, 59, 255, 0.3);
}
.login-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #08060d;
}
.login-header p {
  font-size: 16px;
  color: #6b6375;
  margin-top: 5px;
}

.auth-form {
  margin-bottom: 20px;
}
.auth-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  background: linear-gradient(135deg, #aa3bff 0%, #7928ca 100%) !important;
  border: none !important;
}
.auth-btn:hover {
  opacity: 0.9;
}
.auth-footer {
  text-align: center;
  font-size: 14px;
  color: #666;
}
.auth-footer span {
  margin-right: 5px;
}

/* 2. 主门户界面 */
.portal-wrapper {
  display: flex;
  flex: 1;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  background-color: #1e1e24;
  color: #cdd6f4;
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  box-shadow: 4px 0 25px rgba(0,0,0,0.15);
}
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 35px;
  padding-left: 8px;
}
.logo-icon {
  width: 38px;
  height: 38px;
  background: linear-gradient(135deg, #c084fc 0%, #aa3bff 100%);
  border-radius: 10px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: 800;
  color: white;
  font-size: 18px;
}
.title-box h3 {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}
.version-tag {
  font-size: 10px;
  background: rgba(255,255,255,0.1);
  padding: 2px 6px;
  border-radius: 4px;
  color: #c084fc;
}
.sidebar-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  border: none;
  background: transparent;
  color: #9399b2;
  font-size: 17px;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
  transition: all 0.3s ease;
}
.menu-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #f5e0dc;
}
.menu-item.active {
  background: rgba(170, 59, 255, 0.15);
  color: #c084fc;
  font-weight: 600;
}
.sidebar-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(255,255,255,0.08);
  padding-top: 20px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-avatar {
  background-color: #aa3bff;
  color: white;
  font-weight: 700;
}
.user-detail {
  display: flex;
  flex-direction: column;
}
.user-name {
  font-size: 16px;
  color: white;
  font-weight: 600;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-role {
  font-size: 13px;
  color: #6c7086;
}

.logout-btn {
  background: transparent;
  border: none;
  font-size: 18px;
  color: #f38ba8;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  transition: background 0.3s;
}
.logout-btn:hover {
  background: rgba(243, 139, 168, 0.1);
}

/* 游客登录触发按钮 */
.login-trigger-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
  color: #c084fc;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}
.login-trigger-btn:hover {
  background: rgba(170, 59, 255, 0.15);
  border-color: #c084fc;
  color: white;
}

/* 弹窗登录框样式微调 */
.login-dialog-modal :deep(.el-dialog) {
  background: rgba(255, 255, 255, 0.9) !important;
  backdrop-filter: blur(16px);
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.5) !important;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15) !important;
}
.login-dialog-modal :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
}
.login-card-inside {
  padding: 30px 40px 15px;
}

/* 内容视窗 */
.content-viewport {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  background: rgba(244, 246, 249, 0.6);
}
.tab-content {
  max-width: 100%;
  width: 100%;
  margin: auto; /* 上下左右自动居中 */
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 20px;
  min-height: calc(100vh - 60px); /* 撑满视窗高度 */
}

/* 智能工作台 */
.main-card {
  max-width: 100%;
  width: 100%;
  margin: 0 auto;
  min-height: calc(100vh - 120px); /* 卡片撑满高度 */
  display: flex;
  flex-direction: column;
}
.main-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.card-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #11111b;
}
.card-header .subtitle {
  font-size: 15px;
  color: #6c7086;
  display: block;
  margin-top: 6px;
}
.upload-area {
  margin: 25px 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.upload-area :deep(.el-upload) {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  flex: 1; /* 拖拽区域自适应撑满卡片高度 */
  min-height: 450px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border: 2px dashed rgba(170, 59, 255, 0.3);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.5);
  transition: all 0.3s;
}
.upload-area :deep(.el-upload-dragger):hover {
  border-color: #aa3bff;
  background: rgba(170, 59, 255, 0.02);
}
.upload-area :deep(.el-icon--upload) {
  font-size: 96px;
  color: #aa3bff;
  margin-bottom: 20px;
}
.el-upload__text {
  font-size: 18px;
}
.el-upload__text em {
  color: #aa3bff;
  font-weight: 600;
}


/* 简历诊断卡片 */
.status-section {
  margin-top: 30px;
}
.action-bar {
  text-align: center;
}
.diagnosis-panel {
  padding: 24px;
  background: rgba(255,255,255,0.6);
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.8);
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 20px;
  color: #333;
}
.diagnosis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.diag-card {
  padding: 18px 22px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
}
.diag-card h4 {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.diag-card ul {
  list-style: none;
  padding-left: 0;
}
.diag-card li {
  font-size: 15px;
  line-height: 1.62;
  margin-bottom: 8px;
  position: relative;
  padding-left: 14px;
  color: #4c4f69;
}
.diag-card li::before {
  content: "•";
  position: absolute;
  left: 0;
  font-weight: bold;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  display: inline-block;
}
.dot.green { background-color: #40a02b; }
.dot.orange { background-color: #df8e1d; }
.dot.blue { background-color: #1e66f5; }

.diag-card.advantages {
  background-color: rgba(64, 160, 43, 0.05);
  border-left: 4px solid #40a02b;
}
.diag-card.advantages h4 { color: #40a02b; }

.diag-card.disadvantages {
  background-color: rgba(223, 142, 29, 0.05);
  border-left: 4px solid #df8e1d;
}
.diag-card.disadvantages h4 { color: #df8e1d; }

.diag-card.suggestions {
  background-color: rgba(30, 102, 245, 0.05);
  border-left: 4px solid #1e66f5;
}
.diag-card.suggestions h4 { color: #1e66f5; }

.interview-trigger {
  text-align: center;
  margin-top: 25px;
}
.start-btn {
  padding: 12px 30px;
  font-weight: 600;
  border-radius: 8px;
}

/* 模拟面试对话框 */
.chat-card {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  max-width: 1200px;
  margin: 0 auto;
}
.report-card {
  max-width: 1200px;
  margin: 0 auto;
}
:deep(.chat-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.interviewer-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.interviewer-info h3 {
  font-size: 18px;
  font-weight: 700;
}
.interviewer-info .desc {
  font-size: 13px;
  color: #6c7086;
}
.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 5px;
  background-color: #40a02b;
}
.status-dot.pulsing {
  box-shadow: 0 0 0 0 rgba(64, 160, 43, 0.7);
  animation: pulse 1.6s infinite;
}
@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(64, 160, 43, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(64, 160, 43, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(64, 160, 43, 0); }
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background-color: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.message-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 80%;
}
.message-wrapper.assistant {
  align-self: flex-start;
}
.message-wrapper.user {
  align-self: flex-end;
}
.chat-avatar {
  flex-shrink: 0;
  font-weight: 700;
}
.message-bubble {
  padding: 12px 18px;
  border-radius: 12px;
  box-shadow: 0 4px 10px rgba(0,0,0,0.02);
  line-height: 1.62;
  font-size: 15.5px;
  white-space: pre-wrap;
}
.message-wrapper.assistant .message-bubble {
  background-color: white;
  color: #333;
  border: 1px solid #e6e6e6;
  border-top-left-radius: 0;
}
.message-wrapper.assistant .chat-avatar {
  background-color: #aa3bff;
}
.message-wrapper.user .message-bubble {
  background-color: #aa3bff;
  color: white;
  border-top-right-radius: 0;
}
.message-wrapper.user .chat-avatar {
  background-color: #40a02b;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 10px 16px;
  background: white;
  border-radius: 12px;
  align-self: flex-start;
  margin-left: 48px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
  border: 1px solid #e6e6e6;
}
.typing-indicator span {
  width: 6px;
  height: 6px;
  background-color: #aa3bff;
  border-radius: 3px;
  animation: typing 1s infinite alternate;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  from { transform: translateY(0); opacity: 0.4; }
  to { transform: translateY(-5px); opacity: 1; }
}

.chat-input-bar {
  padding: 16px 24px;
  background: white;
  border-top: 1px solid #e6e6e6;
  display: flex;
  gap: 12px;
}

/* 报告界面 */
.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.report-container {
  padding: 10px;
}
.score-section {
  display: flex;
  align-items: center;
  gap: 30px;
  background: rgba(255, 255, 255, 0.5);
  padding: 24px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.9);
}
.score-circle-wrapper {
  flex-shrink: 0;
}
.score-circle {
  width: 150px;
  height: 150px;
  border-radius: 75px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: white;
  box-shadow: inset 0 2px 10px rgba(0,0,0,0.05);
}
.score-num {
  font-size: 52px;
  font-weight: 800;
  line-height: 1.1;
}
.score-label {
  font-size: 13px;
  color: #6c7086;
  font-weight: 600;
}
.score-comment h3 {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 8px;
  color: #333;
}
.summary-text {
  font-size: 14px;
  line-height: 1.6;
  color: #5c5f77;
}

.report-details h3 {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 15px;
  border-left: 4px solid #aa3bff;
  padding-left: 10px;
}
.history-chat-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
  max-height: 400px;
  overflow-y: auto;
  background: #fbfbfb;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e6e6e6;
}
.history-msg-item {
  padding: 16px 20px;
  border-radius: 10px;
  line-height: 1.6;
  font-size: 16px;
}
.history-msg-item.assistant {
  background-color: white;
  border-left: 5px solid #aa3bff;
}
.history-msg-item.user {
  background-color: rgba(64, 160, 43, 0.05);
  border-left: 5px solid #40a02b;
}
.role-badge {
  font-size: 13px;
  font-weight: 700;
  display: block;
  margin-bottom: 6px;
  text-transform: uppercase;
}
.history-msg-item.assistant .role-badge { color: #aa3bff; }
.history-msg-item.user .role-badge { color: #40a02b; }
.msg-text {
  color: #4c4f69;
}

/* 列表展示页样式 */
.list-card-container {
  padding: 24px;
  min-height: calc(100vh - 120px);
}
.tab-header-row {
  margin-bottom: 25px;
}
.tab-header-row h2 {
  font-size: 22px;
  font-weight: 700;
}
.tab-header-row .subtitle {
  font-size: 14px;
  color: #6c7086;
}
.custom-table {
  background: transparent !important;
}
:deep(.el-table) {
  --el-table-border-color: rgba(0,0,0,0.05);
  --el-table-header-bg-color: rgba(255,255,255,0.6);
  --el-table-tr-bg-color: transparent;
}
:deep(.el-table th) {
  font-weight: 700;
  color: #333;
}

/* 弹窗中的评估历史 */
.historic-interview-panel {
  padding: 5px;
}
.compact-score {
  margin-bottom: 20px;
}
.history-title {
  font-size: 15px;
  font-weight: 700;
  margin: 20px 0 10px;
}
.dialog-chat-list {
  max-height: 550px;
}
.dialog-diag {
  background: transparent;
  padding: 0;
  border: none;
}

/* 每日签到与 ⚡ AI 算力能量槽 */
.sidebar-energy-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  margin: 20px 0;
  color: #cdd6f4;
}
.energy-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.energy-title {
  font-size: 13px;
  font-weight: 600;
  color: #f5e0dc;
  letter-spacing: 0.5px;
}
.info-icon {
  font-size: 14px;
  color: #a6adc8;
  cursor: help;
}
.progress-container {
  margin-bottom: 15px;
}
.energy-text {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  margin-top: 6px;
  font-size: 11px;
  color: #a6adc8;
}
.energy-val {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  margin-right: 2px;
}
.energy-max {
  font-weight: 500;
}
.sign-btn {
  width: 100%;
  padding: 10px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #c084fc 0%, #aa3bff 100%);
  color: white;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;
}
.sign-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}
.sign-btn:disabled {
  background: rgba(255, 255, 255, 0.1) !important;
  color: #585b70;
  cursor: not-allowed;
}
.sign-days-tip {
  text-align: center;
  font-size: 11px;
  color: #a6adc8;
  margin-top: 8px;
}
.sign-days-tip .highlight {
  color: #f5e0dc;
  font-weight: 700;
}

@media (max-width: 760px) {
  .portal-wrapper {
    flex-direction: column;
  }

  .sidebar {
    box-sizing: border-box;
    width: 100%;
    min-height: 64px;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    overflow-x: auto;
    box-shadow: 0 4px 18px rgba(0, 0, 0, 0.16);
  }

  .sidebar-header {
    flex: 0 0 auto;
    margin: 0;
    padding: 0;
  }

  .sidebar-header .title-box,
  .sidebar-energy-card,
  .user-detail {
    display: none;
  }

  .logo-icon {
    width: 34px;
    height: 34px;
    border-radius: 7px;
    font-size: 15px;
  }

  .sidebar-menu {
    flex: 0 0 auto;
    flex-direction: row;
    gap: 4px;
  }

  .menu-item {
    flex: 0 0 auto;
    gap: 6px;
    padding: 9px 10px;
    border-radius: 6px;
    font-size: 13px;
    white-space: nowrap;
  }

  .sidebar-footer {
    flex: 0 0 auto;
    margin-left: auto;
    padding: 0;
    border: 0;
  }

  .login-trigger-btn {
    width: auto;
    padding: 8px 10px;
    white-space: nowrap;
  }

  .content-viewport {
    box-sizing: border-box;
    width: 100%;
    min-height: 0;
    padding: 16px;
  }

  .tab-content {
    min-height: calc(100vh - 96px);
    margin: 0;
    justify-content: flex-start;
  }
}

@media (max-width: 520px) {
  .sidebar {
    padding-right: 8px;
  }

  .menu-item {
    padding: 9px 8px;
  }

  .login-trigger-btn span,
  .logout-btn + span {
    display: none;
  }
}
</style>
