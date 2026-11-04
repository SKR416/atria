<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Globe } from 'lucide-vue-next'
import type { SupportedLocale } from '@/types/api'
import { setAppLocale } from '@/i18n'

const { locale } = useI18n()

const options: { label: string; value: SupportedLocale }[] = [
  { label: '繁體中文', value: 'zh-TW' },
  { label: 'English', value: 'en-US' },
  { label: '日本語', value: 'ja-JP' }
]

const currentLocale = computed({
  get: () => locale.value as SupportedLocale,
  set: (val: SupportedLocale) => {
    setAppLocale(val)
  }
})
</script>

<template>
  <div class="atria-lang-switcher">
    <Globe :size="15" class="lang-icon" />
    <select v-model="currentLocale" class="lang-select" aria-label="Language Switcher">
      <option v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.atria-lang-switcher {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  background: var(--atria-surface-subtle, #f8fafc);
  border: 1px solid var(--atria-border, #cbd5e1);
  border-radius: 6px;
  padding: 0.25rem 0.5rem;
  font-size: 0.8125rem;
  color: var(--atria-text, #1e293b);
}

.lang-icon {
  color: var(--atria-primary, #1d4ed8);
  flex-shrink: 0;
}

.lang-select {
  border: none;
  background: transparent;
  font-size: 0.8125rem;
  font-weight: 500;
  color: inherit;
  cursor: pointer;
  outline: none;
}
</style>
