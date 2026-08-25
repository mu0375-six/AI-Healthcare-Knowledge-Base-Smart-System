<template>
  <div class="page">
    <PageHeader title="报告解读" desc="支持 PDF / Word / txt。图片可把识别出的文字贴在下面，或使用文件名含 demo 的示例。" />

    <section class="drop sheet" @dragover.prevent @drop.prevent="onDrop">
      <h3>{{ file ? file.name : '把报告拖到这里，或选择文件' }}</h3>
      <p>系统会抽出指标，并给出逐项说明。选一份档案后，血糖血压等会写进那份档案。</p>
      <el-select v-model="profileId" clearable placeholder="写入哪份档案（可选）" style="width: 240px; margin-bottom: 12px">
        <el-option v-for="p in profiles" :key="p.id" :label="(p.displayName || '档案') + ' · ' + (p.relation || '')" :value="p.id" />
      </el-select>
      <el-upload :auto-upload="false" :limit="1" :show-file-list="false" :on-change="onFile" accept=".pdf,.doc,.docx,.txt,.png,.jpg,.jpeg">
        <button class="ghost-btn" type="button">选择文件</button>
      </el-upload>
      <el-input
        v-model="extracted"
        class="paste"
        type="textarea"
        :rows="4"
        placeholder="可选：粘贴报告文字（图片 OCR 不可用时请粘贴）"
      />
      <button class="copper-btn" type="button" :disabled="!file || uploading" @click="doUpload">
        {{ uploading ? '解读中…' : '上传并解读' }}
      </button>
    </section>

    <section class="block">
      <h3>我的报告</h3>
      <div v-if="!list.length" class="quiet">还没有报告。</div>
      <button v-for="row in list" :key="row.id" class="rep card" type="button" @click="open(row)">
        <b>{{ row.filename }}</b>
        <time>{{ formatWhen(row.createdAt) }}</time>
      </button>
    </section>
    <div class="disclaimer">以上内容仅供健康科普参考，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { listReports, uploadReport } from '@/api/reports'
import { listProfiles } from '@/api/health'
import type { ExamReport, HealthProfile } from '@/api/types'
import { formatWhen } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const route = useRoute()
const list = ref<ExamReport[]>([])
const file = ref<File | null>(null)
const extracted = ref('')
const uploading = ref(false)
const profiles = ref<HealthProfile[]>([])
const profileId = ref<number | undefined>(undefined)

onMounted(load)

async function load() {
  list.value = (await listReports()).data || []
  try {
    profiles.value = (await listProfiles()).data || []
    const q = Number(route.query.profileId)
    if (q) profileId.value = q
  } catch {
    profiles.value = []
  }
}

function onFile(f: UploadFile) {
  file.value = (f.raw as File) || null
}

function onDrop(e: DragEvent) {
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

function open(row: ExamReport) {
  router.push(`/reports/${row.id}`)
}
</script>

<style scoped>
.drop {
  padding: 28px 28px 24px;
  text-align: left;
}
.drop h3 {
  margin: 0 0 8px;
  font-size: 26px;
}
.drop p {
  color: var(--ink-3);
  margin: 0 0 16px;
}
.paste {
  margin: 16px 0;
}
.block {
  margin-top: 22px;
}
.block h3 {
  font-size: 20px;
}
.rep {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  margin-top: 10px;
  cursor: pointer;
}
.rep:hover {
  border-color: #d7b09a;
}
.rep time,
.quiet {
  color: var(--ink-3);
  font-size: 13px;
}

@media (max-width: 720px) {
  .drop {
    padding: 18px 16px 16px;
  }
  .drop h3 {
    font-size: 20px;
  }
  .block h3 {
    font-size: 17px;
  }
  /* 文件名与时间在窄屏挤成一行会互相压扁，改为换行堆叠 */
  .rep {
    flex-wrap: wrap;
    gap: 4px;
    padding: 12px 14px;
  }
}
</style>
