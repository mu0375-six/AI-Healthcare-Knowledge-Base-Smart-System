<template>
  <div class="page">
    <PageHeader kicker="Knowledge" title="知识库" desc="优先接入世界卫生组织、国家卫生健康委等权威公开文本，也可继续上传或录入。" />

    <section class="knowledge-status">
      <div class="sync-copy">
        <span class="status-dot"></span>
        <div>
          <small>知识源状态</small>
          <strong>权威源可同步</strong>
          <p>WHO 与国家卫健委公开文本</p>
        </div>
      </div>
      <div class="status-metric"><span>已入库文档</span><strong class="num">{{ total }}</strong></div>
      <div class="status-metric"><span>当前页</span><strong class="num">{{ page }}</strong></div>
      <button class="btn btn-primary" type="button" :disabled="syncing" @click="doSync">
        {{ syncing ? '同步中…' : '从权威源同步' }}
      </button>
    </section>

    <div class="intake-grid">
      <section class="panel core-pad intake">
        <header class="intake-head"><span v-html="ICONS.upload"></span><div><h3>文件导入</h3><p>PDF、Word 或纯文本</p></div></header>
          <el-form label-position="top">
            <el-form-item label="文件">
              <el-upload :auto-upload="false" :limit="1" :on-change="onFile" accept=".pdf,.doc,.docx,.txt">
                <el-button>选择 PDF / Word / txt</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item label="标题"><el-input v-model="upload.title" /></el-form-item>
            <el-form-item label="分类">
              <el-select v-model="upload.category" style="width: 100%">
                <el-option label="疾病指南" value="疾病指南" />
                <el-option label="药品说明" value="药品说明" />
                <el-option label="科室导诊" value="科室导诊" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源"><el-input v-model="upload.source" /></el-form-item>
            <el-button type="primary" :disabled="!file" :loading="uploading" @click="doUpload">导入并向量化</el-button>
          </el-form>
      </section>
      <section class="panel core-pad intake">
        <header class="intake-head"><span v-html="ICONS.file"></span><div><h3>文本录入</h3><p>直接维护结构化知识</p></div></header>
          <el-form label-position="top">
            <el-form-item label="标题"><el-input v-model="text.title" /></el-form-item>
            <el-form-item label="分类">
              <el-select v-model="text.category" style="width: 100%">
                <el-option label="疾病指南" value="疾病指南" />
                <el-option label="药品说明" value="药品说明" />
                <el-option label="科室导诊" value="科室导诊" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源"><el-input v-model="text.source" /></el-form-item>
            <el-form-item label="正文"><el-input v-model="text.content" type="textarea" :rows="8" /></el-form-item>
            <el-button type="primary" :loading="saving" @click="doText">写入知识库</el-button>
          </el-form>
      </section>
    </div>

    <section class="panel core-pad block">
      <div class="section-head"><h3>已入库文档</h3><span class="count num">{{ total }}</span></div>
      <el-table :data="docs" empty-text="暂无文档">
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="publisher" label="发布机构" width="220" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column label="原文" min-width="220">
          <template #default="{ row }">
            <a
              v-if="row.sourceUrl"
              class="source-link"
              :href="row.sourceUrl"
              :title="row.sourceUrl"
              target="_blank"
              rel="noopener"
            >
              <span class="source-domain">{{ sourceDomain(row.sourceUrl) }}</span>
              <span class="source-action">查看原文<span v-html="ICONS.external"></span></span>
            </a>
            <span v-else>{{ row.source }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatWhen(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="" width="90">
          <template #default="{ row }">
            <el-button text type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > pageSize"
        class="pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="turn"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { addKnowledgeText, deleteKnowledge, listKnowledge, syncOfficialKnowledge, uploadKnowledge } from '@/api/knowledge'
import type { KbDocument } from '@/api/types'
import { formatWhen } from '@/utils/format'
import { ICONS } from '@/utils/icons'
import PageHeader from '@/components/PageHeader.vue'

const docs = ref<KbDocument[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const file = ref<File | null>(null)
const uploading = ref(false)
const saving = ref(false)
const syncing = ref(false)
const upload = reactive({ title: '', category: '疾病指南', source: '' })
const text = reactive({ title: '', category: '疾病指南', source: '后台录入', content: '' })

onMounted(load)

async function load(p = page.value) {
  const res = await listKnowledge(p, pageSize)
  docs.value = res.data?.records || []
  total.value = res.data?.total || 0
  page.value = p
}

function turn(p: number) {
  load(p)
}

function onFile(f: UploadFile) {
  file.value = (f.raw as File) || null
}

async function doUpload() {
  if (!file.value) return
  uploading.value = true
  try {
    await uploadKnowledge(file.value, { ...upload })
    ElMessage.success('已导入')
    await load()
  } finally {
    uploading.value = false
  }
}

async function doText() {
  saving.value = true
  try {
    await addKnowledgeText({ ...text })
    ElMessage.success('已写入')
    text.content = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function doSync() {
  syncing.value = true
  try {
    const res = await syncOfficialKnowledge(true)
    const d = res.data
    ElMessage.success(`同步完成：在线 ${d.fetched}，快照 ${d.fromSnapshot}，跳过 ${d.skipped}，清理演示 ${d.removedDemo}`)
    await load()
  } finally {
    syncing.value = false
  }
}

async function remove(id: number) {
  await deleteKnowledge(id)
  await load()
}

function sourceDomain(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return '原文链接'
  }
}
</script>

<style scoped>
.knowledge-status {
  display: grid;
  grid-template-columns: minmax(260px, 1.5fr) repeat(2, minmax(100px, 0.55fr)) auto;
  align-items: center;
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: var(--card);
  overflow: hidden;
}
.sync-copy {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 76px;
  padding: 12px 16px;
}
.status-dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: var(--r-pill);
  background: var(--flag-normal);
  box-shadow: 0 0 0 4px var(--flag-normal-wash);
}
.sync-copy > div {
  display: grid;
  gap: 1px;
}
.sync-copy small,
.status-metric span {
  color: var(--ink-mute);
  font-size: 11px;
}
.sync-copy strong {
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
}
.sync-copy p {
  margin: 0;
  color: var(--ink-faint);
  font-size: 11.5px;
}
.status-metric {
  display: grid;
  align-content: center;
  gap: 2px;
  min-height: 76px;
  padding: 12px 16px;
  border-left: 1px solid var(--edge);
}
.status-metric strong {
  color: var(--ink);
  font-size: 20px;
  font-weight: 650;
}
.knowledge-status > .btn {
  margin: 0 16px;
}
.intake-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(0, 1.18fr);
  gap: var(--space-4);
  margin-top: var(--space-4);
}
.intake {
  min-width: 0;
}
.intake-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--edge);
}
.intake-head > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--r-control);
  background: var(--accent-wash);
  color: var(--accent);
}
.intake-head > span :deep(svg) {
  width: 17px;
  height: 17px;
}
.intake-head h3 {
  font-size: 15px;
}
.intake-head p {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 11px;
}
.block {
  margin-top: var(--space-4);
}
.pager {
  margin-top: var(--space-3);
  justify-content: center;
}

.source-link {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
  max-width: 100%;
  color: var(--ink-mute);
}

.source-domain {
  max-width: 100%;
  overflow: hidden;
  color: var(--ink-soft);
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--accent);
  font-size: 12px;
}

.source-action :deep(svg) {
  width: 13px;
  height: 13px;
}

@media (max-width: 720px) {
  .knowledge-status,
  .intake-grid {
    grid-template-columns: 1fr;
  }

  .status-metric {
    border-top: 1px solid var(--edge);
    border-left: 0;
  }

  .knowledge-status > .btn {
    margin: 12px 16px 16px;
  }

  /* 列宽之和约 980px，窄屏必然横向滚动，这里只把字号压下来减少滚动距离 */
  .el-table {
    font-size: 12px;
  }
}
</style>
