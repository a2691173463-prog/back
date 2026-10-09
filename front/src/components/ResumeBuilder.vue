<template>
  <div class="resume-builder" :style="{ '--resume-accent': accentColor, '--resume-soft': softColor }">
    <header class="builder-toolbar">
      <div class="builder-title">
        <el-button :icon="ArrowLeft" circle title="返回模板广场" @click="emit('close')" />
        <div>
          <span>{{ template.category || '通用岗位' }}</span>
          <h2>{{ template.name }}</h2>
        </div>
      </div>
      <div class="save-status"><i :class="{ saved: autoSaveStatus === '已自动保存' }" />{{ autoSaveStatus }}</div>
      <div class="toolbar-actions">
        <el-button :icon="DocumentCopy" @click="restoreTemplateExample">载入示例</el-button>
        <el-button :icon="RefreshLeft" @click="restoreCloudDraft">恢复云端</el-button>
        <el-button :icon="DocumentChecked" :loading="cloudSaving" @click="saveCloudDraft">保存云端</el-button>
        <el-button type="primary" :icon="Printer" @click="printResume">导出 PDF</el-button>
      </div>
    </header>

    <main class="builder-workspace">
      <section class="editor-panel">
        <el-tabs v-model="activeSection" stretch>
          <el-tab-pane label="基本信息" name="basic">
            <div class="form-grid two-columns">
              <el-form-item label="姓名"><el-input v-model="draft.personal.name" maxlength="20" /></el-form-item>
              <el-form-item label="目标岗位"><el-input v-model="draft.personal.targetRole" maxlength="40" /></el-form-item>
              <el-form-item label="手机号"><el-input v-model="draft.personal.phone" maxlength="30" /></el-form-item>
              <el-form-item label="邮箱"><el-input v-model="draft.personal.email" maxlength="80" /></el-form-item>
              <el-form-item label="所在城市"><el-input v-model="draft.personal.location" maxlength="30" /></el-form-item>
              <el-form-item label="个人主页"><el-input v-model="draft.personal.website" maxlength="120" /></el-form-item>
            </div>
            <div class="avatar-editor">
              <div class="avatar-editor-preview">
                <img v-if="draft.personal.avatar" :src="draft.personal.avatar" alt="简历头像预览" />
                <span v-else>{{ avatarInitial }}</span>
              </div>
              <div>
                <strong>个人照片</strong>
                <p>建议使用清晰的正面职业照，上传后会自动压缩并保存在草稿中。</p>
                <div class="avatar-actions">
                  <el-button :icon="Upload" @click="avatarInput?.click()">上传照片</el-button>
                  <el-button v-if="draft.personal.avatar" :icon="Delete" text @click="draft.personal.avatar = ''">移除</el-button>
                </div>
                <input ref="avatarInput" class="avatar-file-input" type="file" accept="image/jpeg,image/png,image/webp" @change="handleAvatarUpload" />
              </div>
            </div>
            <el-form-item label="个人优势">
              <el-input v-model="draft.summary" type="textarea" :rows="5" maxlength="800" show-word-limit />
            </el-form-item>
            <el-button :icon="MagicStick" :loading="optimizingKey === 'summary'" @click="optimizeText('个人优势', draft.summary, value => draft.summary = value, 'summary')">AI 优化这段</el-button>
          </el-tab-pane>

          <el-tab-pane label="教育经历" name="education">
            <div v-for="(item, index) in draft.education" :key="item.uid" class="repeat-section">
              <div class="repeat-heading"><strong>教育经历 {{ index + 1 }}</strong><el-button :icon="Delete" text title="删除" @click="draft.education.splice(index, 1)" /></div>
              <div class="form-grid two-columns">
                <el-form-item label="学校"><el-input v-model="item.school" /></el-form-item>
                <el-form-item label="专业"><el-input v-model="item.major" /></el-form-item>
                <el-form-item label="学历"><el-input v-model="item.degree" /></el-form-item>
                <el-form-item label="时间"><el-input v-model="item.period" placeholder="2024.09 - 2027.06" /></el-form-item>
              </div>
              <el-form-item label="课程、排名或荣誉"><el-input v-model="item.details" type="textarea" :rows="3" /></el-form-item>
            </div>
            <el-button :icon="Plus" @click="draft.education.push(newEducation())">添加教育经历</el-button>
          </el-tab-pane>

          <el-tab-pane label="项目经历" name="projects">
            <div v-for="(item, index) in draft.projects" :key="item.uid" class="repeat-section">
              <div class="repeat-heading"><strong>项目 {{ index + 1 }}</strong><el-button :icon="Delete" text title="删除" @click="draft.projects.splice(index, 1)" /></div>
              <div class="form-grid two-columns">
                <el-form-item label="项目名称"><el-input v-model="item.name" /></el-form-item>
                <el-form-item label="担任角色"><el-input v-model="item.role" /></el-form-item>
                <el-form-item label="项目时间"><el-input v-model="item.period" /></el-form-item>
                <el-form-item label="技术栈"><el-input v-model="item.tech" placeholder="Spring Boot / Redis / MySQL" /></el-form-item>
              </div>
              <el-form-item label="职责与成果">
                <el-input v-model="item.description" type="textarea" :rows="6" maxlength="1600" show-word-limit placeholder="建议按“问题 - 行动 - 结果”描述，每项成果单独一行" />
              </el-form-item>
              <el-button :icon="MagicStick" :loading="optimizingKey === `project-${item.uid}`" @click="optimizeText('项目经历', item.description, value => item.description = value, `project-${item.uid}`)">AI 优化项目描述</el-button>
            </div>
            <el-button :icon="Plus" @click="draft.projects.push(newProject())">添加项目经历</el-button>
          </el-tab-pane>

          <el-tab-pane label="实习经历" name="experience">
            <div v-for="(item, index) in draft.experiences" :key="item.uid" class="repeat-section">
              <div class="repeat-heading"><strong>实习经历 {{ index + 1 }}</strong><el-button :icon="Delete" text title="删除" @click="draft.experiences.splice(index, 1)" /></div>
              <div class="form-grid two-columns">
                <el-form-item label="公司/组织"><el-input v-model="item.company" /></el-form-item>
                <el-form-item label="岗位"><el-input v-model="item.role" /></el-form-item>
                <el-form-item label="时间"><el-input v-model="item.period" /></el-form-item>
                <el-form-item label="地点"><el-input v-model="item.location" /></el-form-item>
              </div>
              <el-form-item label="工作内容与成果"><el-input v-model="item.description" type="textarea" :rows="5" maxlength="1400" show-word-limit /></el-form-item>
              <el-button :icon="MagicStick" :loading="optimizingKey === `experience-${item.uid}`" @click="optimizeText('实习经历', item.description, value => item.description = value, `experience-${item.uid}`)">AI 优化实习描述</el-button>
            </div>
            <el-button :icon="Plus" @click="draft.experiences.push(newExperience())">添加实习经历</el-button>
          </el-tab-pane>

          <el-tab-pane label="技能与荣誉" name="skills">
            <el-form-item label="专业技能">
              <el-input v-model="draft.skills" type="textarea" :rows="8" maxlength="1800" show-word-limit placeholder="按类别分行填写，例如：Java：熟悉集合、并发与 JVM 基础" />
            </el-form-item>
            <el-form-item label="荣誉奖项">
              <el-input v-model="draft.awards" type="textarea" :rows="6" maxlength="1200" show-word-limit />
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section ref="previewStage" class="preview-stage">
        <article class="resume-print-sheet" :class="templateClass" :style="{ '--preview-scale': previewScale }">
          <header class="resume-header">
            <div class="resume-avatar">
              <img v-if="draft.personal.avatar" :src="draft.personal.avatar" alt="个人照片" />
              <span v-else>{{ avatarInitial }}</span>
            </div>
            <div class="resume-identity">
              <h1>{{ draft.personal.name || '你的姓名' }}</h1>
              <strong>{{ draft.personal.targetRole || template.category || '目标岗位' }}</strong>
              <div class="resume-contacts">
                <span v-if="draft.personal.phone">电话：{{ draft.personal.phone }}</span>
                <span v-if="draft.personal.email">邮箱：{{ draft.personal.email }}</span>
                <span v-if="draft.personal.location">城市：{{ draft.personal.location }}</span>
                <span v-if="draft.personal.website">主页：{{ draft.personal.website }}</span>
                <span v-if="!contactLine">手机号 · 邮箱 · 所在城市 · 个人主页</span>
              </div>
            </div>
            <i class="header-line line-one" />
            <i class="header-line line-two" />
          </header>

          <ResumeSection v-if="draft.summary" title="个人优势"><p class="pre-line">{{ draft.summary }}</p></ResumeSection>
          <ResumeSection v-if="hasEducation" title="教育经历">
            <div v-for="item in nonEmptyEducation" :key="item.uid" class="resume-entry">
              <div class="entry-heading"><b>{{ item.school || '学校名称' }}</b><span>{{ item.period }}</span></div>
              <div class="entry-sub"><span>{{ [item.degree, item.major].filter(Boolean).join(' · ') }}</span></div>
              <p v-if="item.details" class="pre-line">{{ item.details }}</p>
            </div>
          </ResumeSection>
          <ResumeSection v-if="hasProjects" title="项目经历">
            <div v-for="item in nonEmptyProjects" :key="item.uid" class="resume-entry">
              <div class="entry-heading"><b>{{ item.name || '项目名称' }}</b><span>{{ item.period }}</span></div>
              <div class="entry-sub"><span>{{ item.role }}</span><em>{{ item.tech }}</em></div>
              <p v-if="item.description" class="pre-line bullet-copy">{{ item.description }}</p>
            </div>
          </ResumeSection>
          <ResumeSection v-if="hasExperiences" title="实习经历">
            <div v-for="item in nonEmptyExperiences" :key="item.uid" class="resume-entry">
              <div class="entry-heading"><b>{{ item.company || '公司名称' }}</b><span>{{ item.period }}</span></div>
              <div class="entry-sub"><span>{{ item.role }}</span><em>{{ item.location }}</em></div>
              <p v-if="item.description" class="pre-line bullet-copy">{{ item.description }}</p>
            </div>
          </ResumeSection>
          <ResumeSection v-if="draft.skills" title="专业技能"><p class="pre-line bullet-copy">{{ draft.skills }}</p></ResumeSection>
          <ResumeSection v-if="draft.awards" title="荣誉奖项"><p class="pre-line bullet-copy">{{ draft.awards }}</p></ResumeSection>
        </article>
      </section>

      <aside class="quality-panel">
        <div class="quality-score">
          <el-progress type="circle" :percentage="atsScore" :width="88" :stroke-width="8" />
          <div><strong>ATS 完成度</strong><p>{{ atsSummary }}</p></div>
        </div>
        <div class="quality-list">
          <div v-for="check in atsChecks" :key="check.label" :class="{ passed: check.passed }">
            <el-icon><CircleCheck v-if="check.passed" /><Warning v-else /></el-icon>
            <span><b>{{ check.label }}</b><small>{{ check.tip }}</small></span>
          </div>
        </div>
        <div class="quality-note">
          <b>示例仅供参考</b>
          <p>模板内姓名、联系方式和经历均为虚构示例。请替换为真实信息，并确保每一项都能在面试中解释。</p>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, CircleCheck, Delete, DocumentChecked, DocumentCopy, MagicStick, Plus, Printer, RefreshLeft, Upload, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { getResumeExample, type Education, type Experience, type Project, type ResumeContent, type TemplateInfo } from '../data/resumeExamples'

const props = defineProps<{ template: TemplateInfo }>()
const emit = defineEmits<{ (event: 'close'): void }>()
const uid = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`
const newEducation = (): Education => ({ uid: uid(), school: '', major: '', degree: '', period: '', details: '' })
const newProject = (): Project => ({ uid: uid(), name: '', role: '', period: '', tech: '', description: '' })
const newExperience = (): Experience => ({ uid: uid(), company: '', role: '', period: '', location: '', description: '' })
const blankDraft = (): ResumeContent => ({
  personal: { name: '', targetRole: props.template.category || '', phone: '', email: '', location: '', website: '', avatar: '' },
  summary: '', education: [newEducation()], projects: [newProject()], experiences: [], skills: '', awards: ''
})

const activeSection = ref('basic')
const draft = ref<ResumeContent>(getResumeExample(props.template))
const draftId = ref<number | null>(null)
const cloudSaving = ref(false)
const optimizingKey = ref('')
const autoSaveStatus = ref('等待编辑')
const avatarInput = ref<HTMLInputElement | null>(null)
const previewStage = ref<HTMLElement | null>(null)
const previewScale = ref(1)
let previewResizeObserver: ResizeObserver | undefined
let saveTimer: ReturnType<typeof setTimeout> | undefined
const localKey = computed(() => `resume-builder:draft:${props.template.id}`)
const accentColor = computed(() => {
  const category = props.template.category || ''
  if (/PM|产品/i.test(category)) return '#8b477d'
  if (/Agent|算法|人工智能/i.test(category)) return '#345d8c'
  if (/前端|设计/i.test(category)) return '#267776'
  if (/数据/i.test(category)) return '#376e59'
  if (/测试/i.test(category)) return '#805b32'
  if (/运维/i.test(category)) return '#526070'
  return '#b52f55'
})
const softColor = computed(() => `${accentColor.value}14`)
const templateClass = computed(() => ({
  product: /PM|产品/i.test(props.template.category || ''),
  technical: /Agent|算法|人工智能|运维/i.test(props.template.category || ''),
  creative: /前端|设计/i.test(props.template.category || '')
}))
const contactLine = computed(() => [draft.value.personal.phone, draft.value.personal.email, draft.value.personal.location, draft.value.personal.website].filter(Boolean).join(' · '))
const avatarInitial = computed(() => (draft.value.personal.name || '简').trim().slice(0, 1))
const fitPreviewToStage = () => {
  if (!previewStage.value) return
  const styles = window.getComputedStyle(previewStage.value)
  const horizontalPadding = Number.parseFloat(styles.paddingLeft) + Number.parseFloat(styles.paddingRight)
  const availableWidth = previewStage.value.clientWidth - horizontalPadding - 2
  const a4Width = 210 * 96 / 25.4
  previewScale.value = Math.min(1, Math.max(0.5, availableWidth / a4Width))
}
const nonEmptyEducation = computed(() => draft.value.education.filter(item => item.school || item.major || item.details))
const nonEmptyProjects = computed(() => draft.value.projects.filter(item => item.name || item.description))
const nonEmptyExperiences = computed(() => draft.value.experiences.filter(item => item.company || item.description))
const hasEducation = computed(() => nonEmptyEducation.value.length > 0)
const hasProjects = computed(() => nonEmptyProjects.value.length > 0)
const hasExperiences = computed(() => nonEmptyExperiences.value.length > 0)

const atsChecks = computed(() => [
  { label: '联系方式完整', passed: Boolean(draft.value.personal.phone && draft.value.personal.email), tip: '手机号和邮箱便于招聘方联系' },
  { label: '求职方向明确', passed: Boolean(draft.value.personal.targetRole), tip: '填写具体岗位而不是宽泛方向' },
  { label: '教育信息完整', passed: nonEmptyEducation.value.some(item => item.school && item.major && item.degree), tip: '至少包含学校、专业和学历' },
  { label: '项目有技术细节', passed: nonEmptyProjects.value.some(item => item.tech && item.description.length >= 60), tip: '说明技术方案、个人行动和结果' },
  { label: '包含量化成果', passed: /\d+[%倍万+]?/.test(draft.value.projects.map(item => item.description).join(' ')), tip: '只填写真实可解释的数据' },
  { label: '技能与岗位匹配', passed: draft.value.skills.length >= 30, tip: '按语言、框架、中间件分类' }
])
const atsScore = computed(() => Math.round(atsChecks.value.filter(item => item.passed).length / atsChecks.value.length * 100))
const atsSummary = computed(() => atsScore.value >= 85 ? '结构完整，可以进入校对' : atsScore.value >= 60 ? '主体已成形，继续补充细节' : '先完成关键信息')

watch(draft, () => {
  autoSaveStatus.value = '正在保存...'
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    localStorage.setItem(localKey.value, JSON.stringify(draft.value))
    autoSaveStatus.value = '已自动保存'
  }, 450)
}, { deep: true })

const normalizeDraft = (value: Partial<ResumeContent>): ResumeContent => ({
  ...blankDraft(), ...value,
  personal: { ...blankDraft().personal, ...(value.personal || {}) },
  education: Array.isArray(value.education) ? value.education : [newEducation()],
  projects: Array.isArray(value.projects) ? value.projects : [newProject()],
  experiences: Array.isArray(value.experiences) ? value.experiences : []
})

const loadJson = (content: string) => {
  draft.value = normalizeDraft(JSON.parse(content))
}

const saveCloudDraft = async () => {
  cloudSaving.value = true
  try {
    const response = await axios.post('/api/resume-drafts', {
      id: draftId.value,
      templateId: props.template.id,
      title: `${draft.value.personal.name || '未命名'} - ${draft.value.personal.targetRole || props.template.name}`,
      contentJson: JSON.stringify(draft.value),
      status: atsScore.value >= 85 ? 1 : 0
    })
    if (response.data.code !== 200) throw new Error(response.data.message)
    draftId.value = response.data.data.id
    ElMessage.success('草稿已保存到云端')
  } catch (error: any) {
    ElMessage.error(error?.response?.status === 401 ? '登录后才能保存云端草稿' : (error.message || '云端保存失败'))
  } finally {
    cloudSaving.value = false
  }
}

const restoreCloudDraft = async () => {
  try {
    const response = await axios.get('/api/resume-drafts')
    if (response.data.code !== 200) throw new Error(response.data.message)
    const record = response.data.data.find((item: any) => item.templateId === props.template.id)
    if (!record) return ElMessage.info('该模板暂无云端草稿')
    await ElMessageBox.confirm('恢复云端草稿会覆盖当前编辑内容，是否继续？', '恢复草稿', { type: 'warning' })
    loadJson(record.contentJson)
    draftId.value = record.id
    ElMessage.success('已恢复云端草稿')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.status === 401 ? '登录后才能恢复云端草稿' : (error.message || '恢复失败'))
  }
}

const restoreTemplateExample = async () => {
  try {
    await ElMessageBox.confirm('载入完整示例会覆盖当前编辑内容，是否继续？', '载入模板示例', { type: 'warning' })
    draft.value = getResumeExample(props.template)
    draftId.value = null
    ElMessage.success('已载入完整示例，请替换为你的真实经历')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('载入示例失败')
  }
}

const handleAvatarUpload = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) return ElMessage.warning('照片大小不能超过 5MB')

  const reader = new FileReader()
  reader.onload = () => {
    const image = new Image()
    image.onload = () => {
      const canvas = document.createElement('canvas')
      const targetRatio = 4 / 5
      const sourceRatio = image.width / image.height
      let sourceWidth = image.width
      let sourceHeight = image.height
      let sourceX = 0
      let sourceY = 0
      if (sourceRatio > targetRatio) {
        sourceWidth = image.height * targetRatio
        sourceX = (image.width - sourceWidth) / 2
      } else {
        sourceHeight = image.width / targetRatio
        sourceY = (image.height - sourceHeight) / 2
      }
      canvas.width = 320
      canvas.height = 400
      canvas.getContext('2d')?.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, 0, 0, canvas.width, canvas.height)
      draft.value.personal.avatar = canvas.toDataURL('image/jpeg', 0.82)
      ElMessage.success('照片已添加到简历')
    }
    image.onerror = () => ElMessage.error('无法读取这张图片')
    image.src = String(reader.result)
  }
  reader.readAsDataURL(file)
}

const optimizeText = async (sectionType: string, content: string, apply: (value: string) => void, key: string) => {
  if (!content.trim()) return ElMessage.warning('请先填写需要优化的内容')
  optimizingKey.value = key
  try {
    const response = await axios.post('/api/templates/optimize', { sectionType, targetRole: draft.value.personal.targetRole, content })
    if (response.data.code !== 200) throw new Error(response.data.message)
    apply(response.data.data)
    ElMessage.success('AI 已完成优化，请核对事实和数据')
  } catch (error: any) {
    ElMessage.error(error?.response?.status === 401 ? '登录后才能使用 AI 优化' : (error.message || 'AI 优化失败'))
  } finally {
    optimizingKey.value = ''
  }
}

const printResume = () => {
  if (!draft.value.personal.name) ElMessage.warning('建议先填写姓名再导出')
  window.print()
}

const ResumeSection = defineComponent({
  name: 'ResumeSection', props: { title: { type: String, required: true } },
  setup(sectionProps, { slots }) { return () => h('section', { class: 'resume-section' }, [h('h3', sectionProps.title), slots.default?.()]) }
})

onMounted(() => {
  previewResizeObserver = new ResizeObserver(fitPreviewToStage)
  if (previewStage.value) previewResizeObserver.observe(previewStage.value)
  fitPreviewToStage()
  const saved = localStorage.getItem(localKey.value)
  if (!saved) return
  try { loadJson(saved); autoSaveStatus.value = '已恢复本地草稿' } catch { localStorage.removeItem(localKey.value) }
})
onBeforeUnmount(() => {
  previewResizeObserver?.disconnect()
  if (saveTimer) clearTimeout(saveTimer)
})
</script>

<style scoped>
.resume-builder { min-height: 100vh; background: #f1f3f5; color: #25272b; }
.builder-toolbar { position: sticky; z-index: 10; top: 0; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 18px; min-height: 68px; padding: 10px 18px; border-bottom: 1px solid #dfe3e8; background: rgba(255,255,255,.96); }
.builder-title { display: flex; align-items: center; gap: 12px; min-width: 0; }
.builder-title span { color: var(--resume-accent); font-size: 11px; font-weight: 700; }
.builder-title h2 { overflow: hidden; margin: 2px 0 0; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.save-status { display: flex; align-items: center; gap: 7px; color: #747b86; font-size: 12px; }
.save-status i { width: 7px; height: 7px; border-radius: 50%; background: #d19535; }
.save-status i.saved { background: #3e8b5c; }
.toolbar-actions { display: flex; justify-content: flex-end; gap: 8px; }
.toolbar-actions .el-button { margin: 0; }
.builder-workspace { display: grid; grid-template-columns: minmax(320px,.65fr) minmax(0,1.6fr) minmax(220px,.5fr); align-items: start; gap: 16px; padding: 16px; }
.editor-panel, .quality-panel { border: 1px solid #dfe3e8; border-radius: 7px; background: #fff; }
.editor-panel { min-width: 0; min-height: calc(100vh - 100px); padding: 4px 18px 20px; overflow: hidden; }
.editor-panel :deep(.el-form-item) { display: block; }
.editor-panel :deep(.el-form-item__label) { display: block; width: 100%; height: auto; margin-bottom: 5px !important; color: #3b4047 !important; font-size: 13px !important; line-height: 19px; }
.editor-panel :deep(.el-form-item__content) { display: block; width: 100%; margin-left: 0 !important; }
.editor-panel :deep(.el-input) { font-size: 14px !important; }
.editor-panel :deep(.el-input__wrapper) { padding: 1px 10px !important; }
.editor-panel :deep(.el-input__inner) { height: 34px !important; font-size: 14px !important; }
.editor-panel :deep(.el-textarea__inner) { padding: 9px 10px !important; font-size: 14px !important; line-height: 1.55 !important; }
.resume-builder :deep(.el-button) { font-size: 13px !important; }
.form-grid { display: grid; gap: 0 12px; }
.two-columns { grid-template-columns: 1fr 1fr; }
.repeat-section { margin-bottom: 16px; padding: 13px; border: 1px solid #e0e4e9; border-radius: 6px; background: #fafbfc; }
.repeat-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.repeat-heading strong { font-size: 13px; }
.avatar-editor { display: grid; grid-template-columns: 72px 1fr; gap: 13px; align-items: center; margin: 2px 0 18px; padding: 12px; border: 1px solid #e0e4e9; border-radius: 6px; background: #fafbfc; }
.avatar-editor-preview { display: flex; width: 64px; height: 80px; align-items: center; justify-content: center; overflow: hidden; border: 1px solid #d8dde4; background: var(--resume-soft); color: var(--resume-accent); font-size: 24px; font-weight: 700; }
.avatar-editor-preview img { width: 100%; height: 100%; object-fit: cover; }
.avatar-editor strong { font-size: 13px; }
.avatar-editor p { margin: 4px 0 9px; color: #777f89; font-size: 11px; line-height: 1.5; }
.avatar-actions { display: flex; gap: 6px; }
.avatar-file-input { display: none; }
.preview-stage { position: relative; display: block; min-width: 0; min-height: calc(100vh - 100px); padding: 16px; overflow-x: hidden; overflow-y: auto; background: #e3e6ea; text-align: center; }
.resume-print-sheet { box-sizing: border-box; width: 210mm; min-height: 297mm; margin: 0 auto; padding: 15mm 16mm; overflow: hidden; background: #fff; box-shadow: 0 7px 24px rgba(20,25,31,.15); color: #252b30; font-family: Arial, "Microsoft YaHei", sans-serif; text-align: left; zoom: var(--preview-scale); }
.resume-header { position: relative; display: grid; grid-template-columns: 29mm minmax(0, 1fr); gap: 11mm; min-height: 39mm; align-items: center; margin: -15mm -16mm 3mm; padding: 8mm 16mm 7mm; overflow: hidden; border-bottom: 1px solid var(--resume-accent); background: #f7f8fa; }
.resume-header::after { position: absolute; z-index: 0; inset: 0 0 0 43mm; background: var(--resume-accent); content: ''; }
.resume-avatar { position: relative; z-index: 2; display: flex; width: 27mm; height: 34mm; align-items: center; justify-content: center; overflow: hidden; border: 1.5mm solid #fff; background: var(--resume-soft); box-shadow: 0 2px 8px rgba(28,32,38,.18); color: var(--resume-accent); font-size: 28px; font-weight: 800; transform: translateX(-6mm); }
.resume-avatar img { width: 100%; height: 100%; object-fit: cover; }
.resume-identity { position: relative; z-index: 2; min-width: 0; color: #fff; }
.resume-header h1 { margin: 0; font-size: 32px; line-height: 1.2; letter-spacing: 0; }
.resume-header strong { display: block; margin-top: 5px; color: rgba(255,255,255,.9); font-size: 16px; }
.resume-contacts { display: flex; flex-wrap: wrap; gap: 5px 18px; margin-top: 11px; color: rgba(255,255,255,.94); font-size: 11.5px; line-height: 1.5; }
.resume-contacts span { min-width: 34%; }
.header-line { position: absolute; z-index: 1; width: 74mm; height: 74mm; border: 1px solid rgba(255,255,255,.16); border-radius: 50%; pointer-events: none; }
.line-one { top: -59mm; right: -6mm; }
.line-two { top: -54mm; right: -14mm; }
.resume-section { margin-top: 16px; }
.resume-section h3 { margin: 0 0 9px; padding: 5px 9px; border-left: 4px solid var(--resume-accent); background: var(--resume-soft); color: var(--resume-accent); font-size: 15px; line-height: 1.3; }
.resume-section p { margin: 5px 0; font-size: 13.5px; line-height: 1.72; }
.resume-entry { margin-bottom: 13px; }
.entry-heading, .entry-sub { display: flex; justify-content: space-between; gap: 12px; }
.entry-heading { align-items: baseline; font-size: 14px; }
.entry-heading b { color: #24292f; font-size: 14px; }
.entry-heading span, .entry-sub { color: #5f6872; font-size: 12px; line-height: 1.55; }
.entry-sub em { font-style: normal; }
.pre-line { white-space: pre-line; }
.bullet-copy { padding-left: 10px; }
.quality-panel { padding: 16px; }
.quality-score { display: flex; align-items: center; gap: 12px; padding-bottom: 16px; border-bottom: 1px solid #e4e7eb; }
.quality-score strong { font-size: 16px; }
.quality-score p { margin: 6px 0 0; color: #68717c; font-size: 13px; line-height: 1.5; }
.quality-list { display: grid; gap: 16px; padding: 20px 0; }
.quality-list > div { display: flex; align-items: flex-start; gap: 8px; color: #a06c25; }
.quality-list > div.passed { color: #347854; }
.quality-list span { display: grid; gap: 3px; color: #34383d; }
.quality-list b { font-size: 14px; }
.quality-list small { color: #737c87; font-size: 12px; line-height: 1.5; }
.quality-note { padding: 12px; border-left: 3px solid var(--resume-accent); background: #f5f6f8; }
.quality-note b { font-size: 14px; }
.quality-note p { margin: 6px 0 0; color: #5e6772; font-size: 12px; line-height: 1.6; }
@media screen and (max-width: 1150px) {
  .builder-workspace { grid-template-columns: minmax(340px,.72fr) minmax(0,1.28fr); }
  .quality-panel { display: grid; grid-column: 1 / -1; grid-template-columns: 240px minmax(0,1fr) 290px; gap: 20px; align-items: start; }
  .quality-score { height: 100%; padding: 0 20px 0 0; border-right: 1px solid #e4e7eb; border-bottom: 0; }
  .quality-list { grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px 18px; padding: 0; }
  .quality-note { align-self: stretch; }
}
@media screen and (max-width: 980px) {
  .builder-toolbar { grid-template-columns: 1fr; gap: 10px; padding: 10px 12px; }
  .save-status { display: none; }
  .toolbar-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; gap: 6px; overflow: hidden; }
  .toolbar-actions .el-button { width: 100%; padding: 8px 5px; font-size: 12px; }
  .builder-workspace { min-width: 0; grid-template-columns: minmax(0, 1fr); padding: 10px; overflow: hidden; }
  .quality-panel { display: block; grid-column: auto; }
  .quality-score { height: auto; padding: 0 0 16px; border-right: 0; border-bottom: 1px solid #e4e7eb; }
  .quality-list { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); padding: 18px 0; }
  .editor-panel { min-height: 0; padding: 4px 12px 16px; }
  .preview-stage { min-width: 0; min-height: 0; padding: 10px; }
  .resume-print-sheet { width: 100%; min-height: auto; padding: 9mm; zoom: 1; }
  .resume-header { grid-template-columns: 23mm minmax(0, 1fr); gap: 6mm; margin: -9mm -9mm 3mm; padding: 7mm 9mm 6mm; }
  .resume-header::after { left: 34mm; }
  .resume-avatar { width: 22mm; height: 28mm; transform: none; }
  .resume-header h1 { font-size: 21px; }
  .resume-contacts { gap: 3px 8px; font-size: 8px; }
  .two-columns { grid-template-columns: 1fr; }
}
@media screen and (max-width: 560px) {
  .quality-list { grid-template-columns: 1fr; }
}
@media print {
  :global(html), :global(body), :global(#app) { width: 210mm !important; min-width: 0 !important; margin: 0 !important; padding: 0 !important; overflow: visible !important; background: #fff !important; }
  :global(body > #app) { display: none !important; }
  :global(.el-overlay), :global(.el-overlay-dialog), :global(.resume-builder-dialog), :global(.resume-builder-dialog .el-dialog__body) { position: static !important; display: block !important; width: 210mm !important; max-width: none !important; height: auto !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: visible !important; background: #fff !important; }
  .builder-toolbar, .editor-panel, .quality-panel { display: none !important; }
  .resume-builder, .builder-workspace, .preview-stage { display: block !important; width: 210mm !important; min-width: 0 !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: visible !important; background: #fff !important; }
  .resume-print-sheet { position: static !important; display: block !important; width: 210mm !important; min-height: 297mm !important; margin: 0 !important; box-shadow: none !important; transform: none !important; zoom: 1 !important; }
  @page { size: A4; margin: 0; }
}
</style>
