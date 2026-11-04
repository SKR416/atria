import { createI18n } from 'vue-i18n'
import type { SupportedLocale } from '@/types/api'
import zhTW from './locales/zh-TW'
import enUS from './locales/en-US'
import jaJP from './locales/ja-JP'

const LOCALE_STORAGE_KEY = 'atria_locale'
const SUPPORTED_LOCALES: SupportedLocale[] = ['zh-TW', 'en-US', 'ja-JP']

export function getInitialLocale(): SupportedLocale {
  try {
    const saved = localStorage.getItem(LOCALE_STORAGE_KEY) as SupportedLocale | null
    if (saved && SUPPORTED_LOCALES.includes(saved)) {
      return saved
    }
  } catch {
    // ignore storage errors
  }
  return 'zh-TW'
}

export const i18n = createI18n({
  legacy: false,
  locale: getInitialLocale(),
  fallbackLocale: 'zh-TW',
  messages: {
    'zh-TW': zhTW,
    'en-US': enUS,
    'ja-JP': jaJP
  }
})

export function setAppLocale(locale: SupportedLocale) {
  if (!SUPPORTED_LOCALES.includes(locale)) return
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  } catch {
    // ignore storage errors
  }
}

export function getAppLocale(): SupportedLocale {
  return i18n.global.locale.value as SupportedLocale
}
