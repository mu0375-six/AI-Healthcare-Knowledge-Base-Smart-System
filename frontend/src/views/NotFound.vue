<template>
  <div class="page">
    <PageHeader kicker="Navigation" title="这个页面不存在" desc="地址可能已变化，或你打开了一个已经移除的入口。" />

    <section class="not-found" aria-labelledby="not-found-title">
      <div class="code num" aria-hidden="true">404</div>
      <div class="copy">
        <span class="state-icon" aria-hidden="true" v-html="ICONS.compass"></span>
        <p class="eyebrow">路径未找到</p>
        <h2 id="not-found-title">回到健康工作台继续</h2>
        <p>你的档案、问诊记录和报告没有受到影响。</p>
        <div class="actions">
          <router-link class="btn btn-primary" to="/home">返回今天</router-link>
          <router-link class="btn btn-ghost" to="/chat">发起问诊</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import { ICONS } from '@/utils/icons'
</script>

<style scoped>
.not-found {
  min-height: 460px;
  display: grid;
  grid-template-columns: minmax(180px, 0.65fr) minmax(280px, 1fr);
  align-items: center;
  gap: var(--space-7);
  padding: var(--space-7);
  border-block: 1px solid var(--edge);
}

.code {
  color: var(--paper-2);
  font-size: 180px;
  font-weight: 700;
  line-height: 0.8;
  text-align: right;
  text-shadow: 1px 0 var(--edge-strong), -1px 0 var(--edge-strong), 0 1px var(--edge-strong), 0 -1px var(--edge-strong);
}

.copy {
  max-width: 460px;
}

.state-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: var(--space-4);
  border-radius: var(--r-card);
  background: var(--info-wash);
  color: var(--info);
}

.state-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.copy h2 {
  margin: var(--space-2) 0;
  font-size: 25px;
}

.copy > p:not(.eyebrow) {
  color: var(--ink-mute);
  line-height: 1.7;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

@media (max-width: 720px) {
  .not-found {
    min-height: 420px;
    grid-template-columns: 1fr;
    gap: var(--space-5);
    padding: var(--space-6) var(--space-4);
  }

  .code {
    font-size: 112px;
    text-align: left;
  }
}
</style>
