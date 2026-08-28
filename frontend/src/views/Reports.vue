<template>
  <div class="page">
    <header class="head">
      <router-link class="back" :to="backTo">
        <span v-html="ICONS.chevron"></span>返回档案
      </router-link>
      <p class="eyebrow">报告解读</p>
      <h1>把化验单交给系统读一遍</h1>
      <p class="lead">
        多模态模型直接读图，抽出指标、判高低、逐项解读，并写进对应家人的档案。
        识别效果不佳时，把文字贴到下面一样能解读。
      </p>
    </header>

    <!-- 投放区是这页的主行为：虚线边框 + 拖入/已选两个明确状态 -->
    <section
      class="drop"
      :class="{ over: dragging, ready: !!file }"
      @dragover.prevent="dragging = true"
      @dragleave="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="drop-ico" v-html="file ? ICONS.check : ICONS.upload"></span>

      <h3>{{ file ? file.name : '把报告拖到这里' }}</h3>
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

    <section class="panel core-pad opts">
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

      <button class="btn btn-primary btn-block go" type="button" :disabled="!file || uploading" @click="doUpload">
        {{ uploading ? '解读中…' : '上传并解读' }}
        <span v-if="!uploading" v-html="ICONS.arrow"></span>
      </button>
      <p v-if="uploading" class="quiet">正在识别与逐项解读，通常十几秒。</p>
    </section>

    <MedicalDisclaimer />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadReport } from '@/api/reports'
import { listProfiles } from '@/api/health'
import type { HealthProfile } from '@/api/types'
import { ICONS } from '@/utils/icons'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'

const router = useRouter()
const route = useRoute()
const file = ref<File | null>(null)
const extracted = ref('')
const uploading = ref(false)
const profiles = ref<HealthProfile[]>([])
const profileId = ref<number | undefined>(undefined)
const dragging = ref(false)

// 每一页都要有回头路：带上来时的档案 id，返回时还落在同一份档案
const backTo = computed(() => ({
  path: '/health',
  query: { tab: 'reports', ...(profileId.value ? { id: String(profileId.value) } : {}) },
}))

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
  try {
    const res = await uploadReport(file.value, extracted.value, profileId.value)
    ElMessage.success(profileId.value ? '解读完成，指标已写入档案' : '解读完成')
    router.push(`/reports/${res.data.report.id}`)
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 660px;
  display: grid;
  gap: 18px;
}

.head h1 {
  margin: 4px 0 10px;
  max-width: 9em;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-bottom: 14px;
  font-size: 13px;
  font-weight: 550;
  color: var(--ink-mute);
  transition: color 0.15s ease;
}

/* 左向箭头：复用 chevron 旋转，不为一个方向再画一个图标 */
.back :deep(svg) {
  width: 16px;
  height: 16px;
  transform: rotate(90deg);
}

@media (hover: hover) and (pointer: fine) {
  .back:hover {
    color: var(--accent);
  }
}

.lead {
  color: var(--ink-mute);
  line-height: 1.75;
  max-width: 26em;
}

/* ---- 投放区 ---- */
.drop {
  display: grid;
  justify-items: center;
  text-align: center;
  padding: 34px 24px 28px;
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
  margin-bottom: 12px;
}

.drop.ready .drop-ico {
  color: var(--flag-normal);
}

.drop-ico :deep(svg) {
  width: 34px;
  height: 34px;
  display: block;
}

.drop h3 {
  font-size: 19px;
  word-break: break-all;
  max-width: 17em;
}

.formats,
.picked {
  margin-top: 6px;
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
  padding: 0 0 0 8px;
  color: var(--accent);
  font-size: inherit;
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.picks {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 18px;
}

/* ---- 选项 ---- */
.opts {
  display: grid;
  gap: 16px;
}

.go {
  margin-top: 2px;
}

@media (max-width: 720px) {
  .drop {
    padding: 24px 16px 20px;
  }
  .drop h3 {
    font-size: 17px;
  }
  .picks {
    width: 100%;
  }
  .picks .btn {
    flex: 1;
  }
}
</style>
