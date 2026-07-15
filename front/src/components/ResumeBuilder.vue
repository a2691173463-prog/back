<template>
  <div class="resume-builder" :style="{ '--resume-accent': accentColor }">
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

      <section class="preview-stage">
        <article class="resume-print-sheet" :class="templateClass">
          <header class="resume-header">
            <h1>{{ draft.personal.name || '你的姓名' }}</h1>
            <strong>{{ draft.personal.targetRole || template.category || '目标岗位' }}</strong>
            <p>{{ contactLine || '手机号 · 邮箱 · 所在城市 · 个人主页' }}</p>
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
          <b>真实信息优先</b>
          <p>AI 只负责改写，不会替你补造数据。请确认优化后的每一项都能在面试中解释。</p>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, CircleCheck, Delete, DocumentChecked, MagicStick, Plus, Printer, RefreshLeft, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

interface TemplateInfo { id: number; name: string; category?: string }
interface Education { uid: string; school: string; major: string; degree: string; period: string; details: string }
interface Project { uid: string; name: string; role: string; period: string; tech: string; description: string }
interface Experience { uid: string; company: string; role: string; period: string; location: string; description: string }
interface ResumeContent {
  personal: { name: string; targetRole: string; phone: string; email: string; location: string; website: string }
  summary: string
  education: Education[]
  projects: Project[]
  experiences: Experience[]
  skills: string
  awards: string
}

const props = defineProps<{ template: TemplateInfo }>()
const emit = defineEmits<{ (event: 'close'): void }>()
const uid = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`
const newEducation = (): Education => ({ uid: uid(), school: '', major: '', degree: '', period: '', details: '' })
const newProject = (): Project => ({ uid: uid(), name: '', role: '', period: '', tech: '', description: '' })
const newExperience = (): Experience => ({ uid: uid(), company: '', role: '', period: '', location: '', description: '' })
const emptyDraft = (): ResumeContent => ({
  personal: { name: '', targetRole: props.template.category || '', phone: '', email: '', location: '', website: '' },
  summary: '', education: [newEducation()], projects: [newProject()], experiences: [], skills: '', awards: ''
})

const activeSection = ref('basic')
const draft = ref<ResumeContent>(emptyDraft())
const draftId = ref<number | null>(null)
const cloudSaving = ref(false)
const optimizingKey = ref('')
const autoSaveStatus = ref('等待编辑')
let saveTimer: ReturnType<typeof setTimeout> | undefined
const localKey = computed(() => `resume-builder:draft:${props.template.id}`)
const accentColor = computed(() => /PM|产品/i.test(props.template.category || '') ? '#75549b' : /设计/i.test(props.template.category || '') ? '#286f75' : '#28745a')
const templateClass = computed(() => ({ product: /PM|产品/i.test(props.template.category || ''), design: /设计/i.test(props.template.category || '') }))
const contactLine = computed(() => Object.values(draft.value.personal).slice(2).filter(Boolean).join(' · '))
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
  ...emptyDraft(), ...value,
  personal: { ...emptyDraft().personal, ...(value.personal || {}) },
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
  const saved = localStorage.getItem(localKey.value)
  if (!saved) return
  try { loadJson(saved); autoSaveStatus.value = '已恢复本地草稿' } catch { localStorage.removeItem(localKey.value) }
})
onBeforeUnmount(() => { if (saveTimer) clearTimeout(saveTimer) })
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
.builder-workspace { display: grid; grid-template-columns: minmax(340px, .85fr) minmax(540px, 1.25fr) minmax(220px, .55fr); align-items: start; gap: 16px; padding: 16px; }
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
.preview-stage { display: flex; min-height: calc(100vh - 100px); justify-content: center; padding: 24px; overflow: auto; background: #e3e6ea; }
.resume-print-sheet { box-sizing: border-box; width: 210mm; min-height: 297mm; padding: 15mm 16mm; background: #fff; box-shadow: 0 7px 24px rgba(20,25,31,.15); color: #252b30; font-family: Arial, "Microsoft YaHei", sans-serif; }
.resume-header { padding-bottom: 12px; border-bottom: 2px solid var(--resume-accent); }
.resume-header h1 { margin: 0; font-size: 26px; letter-spacing: 0; }
.resume-header strong { display: block; margin-top: 4px; color: var(--resume-accent); font-size: 13px; }
.resume-header p { margin: 7px 0 0; color: #626a73; font-size: 10px; }
.resume-section { margin-top: 13px; }
.resume-section h3 { margin: 0 0 7px; padding-bottom: 4px; border-bottom: 1px solid var(--resume-accent); color: var(--resume-accent); font-size: 13px; }
.resume-section p { margin: 4px 0; font-size: 10.5px; line-height: 1.65; }
.resume-entry { margin-bottom: 9px; }
.entry-heading, .entry-sub { display: flex; justify-content: space-between; gap: 12px; }
.entry-heading { font-size: 11px; }
.entry-heading span, .entry-sub { color: #656d76; font-size: 9.5px; }
.entry-sub em { font-style: normal; }
.pre-line { white-space: pre-line; }
.bullet-copy { padding-left: 10px; }
.quality-panel { padding: 16px; }
.quality-score { display: flex; align-items: center; gap: 12px; padding-bottom: 16px; border-bottom: 1px solid #e4e7eb; }
.quality-score strong { font-size: 14px; }
.quality-score p { margin: 5px 0 0; color: #747b85; font-size: 11px; line-height: 1.5; }
.quality-list { display: grid; gap: 13px; padding: 18px 0; }
.quality-list > div { display: flex; align-items: flex-start; gap: 8px; color: #a06c25; }
.quality-list > div.passed { color: #347854; }
.quality-list span { display: grid; gap: 3px; color: #34383d; }
.quality-list b { font-size: 12px; }
.quality-list small { color: #848b95; font-size: 10px; line-height: 1.4; }
.quality-note { padding: 12px; border-left: 3px solid var(--resume-accent); background: #f5f6f8; }
.quality-note b { font-size: 12px; }
.quality-note p { margin: 5px 0 0; color: #666e78; font-size: 10px; line-height: 1.55; }
@media (max-width: 1250px) { .builder-workspace { grid-template-columns: minmax(330px,.8fr) minmax(520px,1.2fr); } .quality-panel { grid-column: 1 / -1; } }
@media (max-width: 860px) {
  .builder-toolbar { grid-template-columns: 1fr; gap: 10px; padding: 10px 12px; }
  .save-status { display: none; }
  .toolbar-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); width: 100%; gap: 6px; overflow: hidden; }
  .toolbar-actions .el-button { width: 100%; padding: 8px 5px; font-size: 12px; }
  .builder-workspace { min-width: 0; grid-template-columns: minmax(0, 1fr); padding: 10px; overflow: hidden; }
  .editor-panel { min-height: 0; padding: 4px 12px 16px; }
  .preview-stage { min-width: 0; min-height: 0; padding: 10px; }
  .resume-print-sheet { width: 100%; min-height: auto; padding: 9mm; }
  .two-columns { grid-template-columns: 1fr; }
}
@media print {
  :global(body *) { visibility: hidden !important; }
  :global(.resume-print-sheet), :global(.resume-print-sheet *) { visibility: visible !important; }
  :global(.resume-print-sheet) { position: absolute !important; top: 0; left: 0; width: 210mm !important; min-height: 297mm !important; margin: 0 !important; box-shadow: none !important; }
  @page { size: A4; margin: 0; }
}
</style>
