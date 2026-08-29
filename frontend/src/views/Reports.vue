<template>
  <div class="page">
    <PageHeader
      kicker="报告解读"
      title="把化验单交给系统读一遍"
      desc="多模态模型直接读图，抽出指标、判高低、逐项解读，并写进对应家人的档案。识别效果不佳时，把文字贴到下面一样能解读。"
    />

    <!-- 投放区是这页的主行为：虚线边框 + 拖入/已选两个明确状态 -->
    <section
      class="drop"
      :class="{ over: dragging, ready: !!file }"
      @dragover.prevent="dragging = true"
      @dragleave="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="drop-ico" v-html="file ? ICONS.check : ICONS.upload"></span>

      <h2 class="drop-title">{{ file ? file.name : '把报告拖到这里' }}</h2>
      <p v-if="file" class="picked">
        <span class="num">{{ fileSize(file.size) }}</span> · {{ kindLabel(file.name) }} · 已就绪
        <button type="button" class="link" @click="file = null">换一份</button>
      </p>
      <p v-else class="formats">
        支持 <b>PDF</b> · <b>Word</b> · <b>txt</b> · <b>图片</b>
      </p>

      <div class="picks">
        <label class="btn btn-primary" for="cap-report">
          <span v-html="ICONS.camera"></span>拍化验单
          <input id="cap-report" type="file" accept="image/*" capture="environment" hidden @change="pickRaw" />
        </label>
        <label class="btn btn-ghost" for="pick-report">
          <span v-html="ICONS.image"></span>选择文件
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

    <Shell>
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
        <ol v-if="uploading" class="upload-stages" aria-live="polite">
          <li v-for="(stage, i) in UPLOAD_STAGES" :key="stage" :class="{ done: i < uploadStage, active: i === uploadStage }">
            <span class="stage-mark"><span v-if="i < uploadStage" v-html="ICONS.check"></span><template v-else>{{ i + 1 }}</template></span>
            {{ stage }}
          </li>
        </ol>
      </div>
    </Shell>

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
import Shell from '@/components/Shell.vue'

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
  max-width: 660px;
  display: grid;
  gap: var(--space-5);
}

.page :deep(.head) {
  margin-bottom: 0;
}

/* ---- 投放区 ---- */
.drop {
  display: grid;
  justify-items: center;
  text-align: center;
  padding: var(--space-6) var(--space-5);
  border: 1.5px dashed var(--edge-strong);
  border-radius: var(--r-shell);
  background: var(--paper-2);
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.2s var(--ease-out);
}

.drop.over {
  border-color: var(--accent);
  background: var(--accent-wash);
  transform: scale(1.006);
}

.drop.ready {
  border-style: solid;
  border-color: var(--flag-normal-line);
  background: var(--flag-normal-wash);
}

.drop-ico {
  color: var(--ink-faint);
  margin-bottom: var(--space-3);
}

.drop.ready .drop-ico {
  color: var(--flag-normal);
}

.drop-ico :deep(svg) {
  width: 34px;
  height: 34px;
  display: block;
}

.drop-title {
  font-family: var(--font);
  font-size: 21px;
  word-break: break-all;
  max-width: 17em;
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

/* ---- 选项 ---- */
.opts {
  display: grid;
  gap: var(--space-4);
}

.go {
  margin-top: var(--space-1);
}

.upload-stages {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  list-style: none;
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
  .drop {
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
