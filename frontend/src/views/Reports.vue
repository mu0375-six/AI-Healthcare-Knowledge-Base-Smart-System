<template>
  <div class="page">
    <PageHeader
      kicker="报告解读"
      title="把化验单交给系统读一遍"
      desc="多模态模型直接读图，抽出指标、判高低、逐项解读，并写进对应家人的档案。识别效果不佳时，把文字贴到下面一样能解读。"
    />

    <div class="upload-workbench">
      <section
        class="drop"
        :class="{ over: dragging, ready: !!file }"
        @dragover.prevent="dragging = true"
        @dragleave="dragging = false"
        @drop.prevent="onDrop"
      >
        <span class="drop-status">{{ file ? '文件已就绪' : '文件输入' }}</span>
        <span class="drop-ico" v-html="file ? ICONS.check : ICONS.upload"></span>

        <h2 class="drop-title">{{ file ? file.name : '拖入一份检查报告' }}</h2>
        <p v-if="file" class="picked">
          <span class="num">{{ fileSize(file.size) }}</span> · {{ kindLabel(file.name) }}
          <button type="button" class="link" @click="file = null">重新选择</button>
        </p>
        <p v-else class="formats">PDF、Word、文本或清晰的检查单照片</p>

        <div class="picks">
          <label class="btn btn-primary" for="cap-report">
            <span v-html="ICONS.camera"></span>拍摄报告
            <input id="cap-report" type="file" accept="image/*" capture="environment" hidden @change="pickRaw" />
          </label>
          <label class="btn btn-ghost" for="pick-report">
            <span v-html="ICONS.image"></span>浏览文件
            <input
              id="pick-report"
              type="file"
              accept=".pdf,.doc,.docx,.txt,.png,.jpg,.jpeg"
              hidden
              @change="pickRaw"
            />
          </label>
        </div>
      </section>

      <section class="setup panel core-pad">
        <header class="setup-head">
          <span>解读设置</span>
          <strong>{{ profileId ? '将同步到档案' : '仅生成本次解读' }}</strong>
        </header>
        <div class="opts">
        <label class="field">
          <span>写入哪份档案</span>
          <el-select v-model="profileId" clearable placeholder="可选 —— 不选则只做解读，不写入" style="width: 100%">
            <el-option
              v-for="p in profiles"
              :key="p.id"
              :label="(p.displayName || '档案') + (p.relation ? ' · ' + p.relation : '')"
              :value="p.id"
            />
          </el-select>
        </label>

        <label class="field">
          <span>报告文字（可选，图片识别不佳时兜底）</span>
          <el-input v-model="extracted" type="textarea" :rows="4" placeholder="把化验单上的文字粘贴到这里" />
        </label>

        <button class="btn btn-primary btn-cta btn-block go" type="button" :disabled="!file || uploading" @click="doUpload">
          {{ uploading ? '正在解读' : '上传并解读' }}
          <span class="knob" v-html="uploading ? ICONS.clock : ICONS.arrow"></span>
        </button>
        <ol class="upload-stages" aria-live="polite">
          <li v-for="(stage, i) in UPLOAD_STAGES" :key="stage" :class="{ done: uploading && i < uploadStage, active: uploading && i === uploadStage }">
            <span class="stage-mark"><span v-if="i < uploadStage" v-html="ICONS.check"></span><template v-else>{{ i + 1 }}</template></span>
            {{ stage }}
          </li>
        </ol>
        </div>
      </section>
    </div>

    <MedicalDisclaimer />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadReport } from '@/api/reports'
import { listProfiles } from '@/api/health'
import type { HealthProfile } from '@/api/types'
import { ICONS } from '@/utils/icons'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const route = useRoute()
const file = ref<File | null>(null)
const extracted = ref('')
const uploading = ref(false)
const profiles = ref<HealthProfile[]>([])
const profileId = ref<number | undefined>(undefined)
const dragging = ref(false)
const uploadStage = ref(0)
const UPLOAD_STAGES = ['识别报告内容', '提取指标与参考区间', '生成逐项解读'] as const
let stageTimers: number[] = []

function ext(name: string) {
  return (name.split('.').pop() || '').toLowerCase()
}

function kindLabel(name: string) {
  const e = ext(name)
  if (e === 'pdf') return 'PDF 文档'
  if (e === 'doc' || e === 'docx') return 'Word 文档'
  if (['png', 'jpg', 'jpeg', 'webp'].includes(e)) return '化验单照片'
  return '文本'
}

function fileSize(bytes: number) {
  return bytes > 1024 * 1024
    ? (bytes / 1024 / 1024).toFixed(1) + ' MB'
    : Math.max(1, Math.round(bytes / 1024)) + ' KB'
}

onMounted(async () => {
  try {
    profiles.value = (await listProfiles()).data || []
    const q = Number(route.query.profileId)
    if (q) profileId.value = q
  } catch {
    profiles.value = []
  }
})

function pickRaw(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) file.value = f
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) file.value = f
}

async function doUpload() {
  if (!file.value) return
  uploading.value = true
  startStages()
  try {
    const res = await uploadReport(file.value, extracted.value, profileId.value)
    ElMessage.success(profileId.value ? '解读完成，指标已写入档案' : '解读完成')
    router.push(`/reports/${res.data.report.id}`)
  } finally {
    clearStages()
    uploading.value = false
  }
}

function startStages() {
  clearStages()
  uploadStage.value = 0
  stageTimers = [
    window.setTimeout(() => (uploadStage.value = 1), 1800),
    window.setTimeout(() => (uploadStage.value = 2), 5200),
  ]
}

function clearStages() {
  stageTimers.forEach((timer) => window.clearTimeout(timer))
  stageTimers = []
}

onBeforeUnmount(clearStages)
</script>

<style scoped>
.page {
  max-width: 1120px;
  display: grid;
  gap: var(--space-5);
}

.upload-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 0.92fr);
  gap: var(--space-4);
  align-items: stretch;
}

.page :deep(.head) {
  margin-bottom: 0;
}

/* ---- 投放区 ---- */
.drop {
  position: relative;
  min-height: 430px;
  display: grid;
  align-content: center;
  justify-items: center;
  text-align: center;
  padding: 64px var(--space-6) var(--space-6);
  border: 1px dashed var(--edge-strong);
  border-radius: var(--r-shell);
  background: var(--card);
  transition: border-color 0.16s var(--ease-soft), background 0.16s var(--ease-soft);
}

.drop.over {
  border-color: var(--accent);
  background: var(--accent-wash);
}

.drop.ready {
  border-style: solid;
  border-color: var(--flag-normal-line);
  background: var(--flag-normal-wash);
}

.drop-ico {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: var(--space-4);
  border-radius: var(--r-shell);
  background: var(--sunk);
  color: var(--ink-mute);
}

.drop.ready .drop-ico {
  color: var(--flag-normal);
}

.drop-ico :deep(svg) {
  width: 28px;
  height: 28px;
  display: block;
}

.drop-title {
  font-family: var(--font);
  font-size: 20px;
  word-break: break-all;
  max-width: 17em;
}

.drop-status {
  position: absolute;
  top: var(--space-4);
  left: var(--space-4);
  padding: 4px 8px;
  border-radius: var(--r-chip);
  background: var(--sunk);
  color: var(--ink-mute);
  font-size: 11px;
  font-weight: 650;
}

.drop.ready .drop-status {
  background: var(--flag-normal-wash);
  color: var(--flag-normal);
}

.formats,
.picked {
  margin-top: var(--space-2);
  font-size: 13px;
  color: var(--ink-mute);
}

.formats b {
  font-weight: 600;
  color: var(--ink-soft);
}

.picked {
  color: var(--flag-normal);
}

.link {
  border: 0;
  background: none;
  padding: 0 0 0 var(--space-2);
  color: var(--accent);
  font-size: inherit;
  cursor: pointer;
}

.picks {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  justify-content: center;
  margin-top: var(--space-5);
}

.setup {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.setup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--edge);
}

.setup-head span {
  color: var(--ink);
  font-weight: 650;
}

.setup-head strong {
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
}

/* ---- 选项 ---- */
.opts {
  display: grid;
  gap: var(--space-4);
  height: 100%;
}

.go {
  margin-top: auto;
}

.upload-stages {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  list-style: none;
  padding-top: var(--space-3);
  border-top: 1px solid var(--edge);
}

.upload-stages li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--ink-faint);
  font-size: 12px;
  line-height: 1.45;
}

.upload-stages li.active {
  color: var(--accent);
}

.upload-stages li.done {
  color: var(--flag-normal);
}

.stage-mark {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border: 1px solid currentColor;
  border-radius: var(--r-pill);
  font-family: var(--font-mono);
  font-size: 11px;
}

.stage-mark :deep(svg) {
  width: 14px;
  height: 14px;
}

@media (max-width: 720px) {
  .upload-workbench {
    grid-template-columns: 1fr;
  }

  .drop {
    min-height: 330px;
    padding: var(--space-5) var(--space-4);
  }
  .picks {
    width: 100%;
  }
  .picks .btn {
    flex: 1;
  }
  .upload-stages {
    grid-template-columns: 1fr;
  }
}
</style>
