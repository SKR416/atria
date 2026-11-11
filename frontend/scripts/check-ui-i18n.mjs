import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const srcDir = path.resolve(__dirname, '../src')
const localesDir = path.resolve(srcDir, 'i18n/locales')

/**
 * Parses a TypeScript locale object file into a flat list of dot-separated keys.
 * Uses a lightweight brace-depth state machine over object property keys.
 */
function extractLocaleKeys(filePath) {
  const content = fs.readFileSync(filePath, 'utf-8')
  const lines = content.split(/\r?\n/)
  const stack = []
  const keys = new Set()

  for (const rawLine of lines) {
    const line = rawLine.replace(/\/\/.*$/, '').trim()
    if (!line || line.startsWith('export default') || line.startsWith('import ')) {
      continue
    }

    // Count closing braces at start of line
    while (line.startsWith('}') && stack.length > 0) {
      stack.pop()
      break
    }

    // Match key: {
    const objOpenMatch = line.match(/^['"]?([a-zA-Z0-9_-]+)['"]?\s*:\s*\{\s*$/)
    if (objOpenMatch) {
      stack.push(objOpenMatch[1])
      continue
    }

    // Match key: 'value' or key: "value"
    const leafMatch = line.match(/^['"]?([a-zA-Z0-9_-]+)['"]?\s*:\s*['"`]/)
    if (leafMatch) {
      const fullKey = [...stack, leafMatch[1]].join('.')
      keys.add(fullKey)
    }
  }

  return keys
}

function walkFiles(dir, extFilter) {
  const results = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      results.push(...walkFiles(fullPath, extFilter))
    } else if (extFilter(entry.name)) {
      results.push(fullPath)
    }
  }
  return results
}

let hasError = false
function reportError(category, message) {
  console.error(`[UI/i18n Gate FAIL] [${category}] ${message}`)
  hasError = true
}

// 1. Check 100% Trilingual Key Parity (zh-TW.ts, en-US.ts, ja-JP.ts)
const localeFiles = {
  'zh-TW': path.join(localesDir, 'zh-TW.ts'),
  'en-US': path.join(localesDir, 'en-US.ts'),
  'ja-JP': path.join(localesDir, 'ja-JP.ts')
}

const localeKeyMap = {}
for (const [locale, filePath] of Object.entries(localeFiles)) {
  if (!fs.existsSync(filePath)) {
    reportError('LOCALE_FILE', `Missing locale file: ${filePath}`)
  } else {
    localeKeyMap[locale] = extractLocaleKeys(filePath)
  }
}

const baseKeys = localeKeyMap['zh-TW'] || new Set()
for (const otherLocale of ['en-US', 'ja-JP']) {
  const otherKeys = localeKeyMap[otherLocale] || new Set()
  for (const k of baseKeys) {
    if (!otherKeys.has(k)) {
      reportError('I18N_PARITY', `Key "${k}" exists in zh-TW.ts but is MISSING in ${otherLocale}.ts`)
    }
  }
  for (const k of otherKeys) {
    if (!baseKeys.has(k)) {
      reportError('I18N_PARITY', `Key "${k}" exists in ${otherLocale}.ts but is MISSING in zh-TW.ts`)
    }
  }
}

// 2. Scan Vue & TS files for static t('...') references, PrimeVue <Button> bugs, leaked Phase strings, and data-testid
const sourceFiles = walkFiles(srcDir, (name) => name.endsWith('.vue') || name.endsWith('.ts'))

for (const filePath of sourceFiles) {
  const relPath = path.relative(srcDir, filePath).replace(/\\/g, '/')
  const content = fs.readFileSync(filePath, 'utf-8')

  // 2a. Verify static t('foo.bar') keys exist in zh-TW
  const tCallRegex = /\bt\(\s*['"]([a-zA-Z0-9_.-]+)['"]\s*[,)]/g
  let match
  while ((match = tCallRegex.exec(content)) !== null) {
    const key = match[1]
    // Skip dynamic prefix patterns ending with '.'
    if (key.endsWith('.')) continue
    if (!baseKeys.has(key)) {
      reportError('MISSING_T_KEY', `${relPath} references t('${key}'), which does not exist in locale files`)
    }
  }

  if (filePath.endsWith('.vue')) {
    const templateStartMatch = content.match(/<template[^>]*>/)
    const templateStartIndex = templateStartMatch ? templateStartMatch.index + templateStartMatch[0].length : -1
    const templateEndIndex = content.lastIndexOf('</template>')
    const templateContent = (templateStartIndex !== -1 && templateEndIndex > templateStartIndex)
      ? content.substring(templateStartIndex, templateEndIndex)
      : ''

    // 2b. Check PrimeVue <Button> without :label / label that contains raw text alongside #icon slot
    const buttonBlockRegex = /<Button\b([^>]*?)(?:\/>|>([\s\S]*?)<\/Button>)/g
    let btnMatch
    while ((btnMatch = buttonBlockRegex.exec(templateContent)) !== null) {
      const attrs = btnMatch[1] || ''
      const inner = btnMatch[2] || ''
      if (!inner.trim()) continue

      const hasLabelProp = /\blabel\s*=/.test(attrs) || /:label\s*=/.test(attrs)
      const strippedSlots = inner
        .replace(/<template\s+#icon[\s\S]*?<\/template>/g, '')
        .replace(/<!--[\s\S]*?-->/g, '')
        .trim()

      if (!hasLabelProp && strippedSlots.length > 0 && inner.includes('#icon')) {
        reportError(
          'PRIMEVUE_BUTTON_LABEL',
          `${relPath} has a <Button> with #icon slot and inner content without :label prop (causes vertical text stacking)`
        )
      }
    }

    // 2c. Check leaked internal "Phase X" strings in template
    const strippedComments = templateContent.replace(/<!--[\s\S]*?-->/g, '')
    if (/\bPhase\s*[0-9]/i.test(strippedComments)) {
      reportError('LEAKED_PHASE_TEXT', `${relPath} contains leaked "Phase X" text inside <template>`)
    }

    // 2d. Check views have at least one data-testid attribute
    if (relPath.startsWith('views/') && !templateContent.includes('data-testid=')) {
      reportError('MISSING_TESTID', `${relPath} is a view component but has no data-testid attribute`)
    }
  }
}

if (hasError) {
  process.exit(1)
} else {
  console.log(
    `[UI/i18n Gate PASS] Verified ${baseKeys.size} trilingual keys across 3 locales and ${sourceFiles.length} source files.`
  )
}
