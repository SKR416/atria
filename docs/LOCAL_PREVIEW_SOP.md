# 本機前後端服務啟動、瀏覽器預覽與 UI 驗收標準流程 (Local Preview & UI Verification SOP)

> **觸發時機（When to Read）**：
> 1. 當使用者要求「啟動專案看一眼」、「開瀏覽器預覽」或驗證前後端整合畫面時。
> 2. 階段收尾或修復 UI/UX 後，需實際啟動 `8080`（後端）與 `5173`（前端）進行健康檢查時。
> 3. **Subagent 委派說明**：本流程中的「打包與健康探針驗證」屬無須主觀判斷之機械任務，可優先委派給 **`subagent_type: "haiku-scout"`（`model: "haiku", effort: "low"`）** 或是由主對話以 `run_in_background: true` 啟動長駐服務後，交由 `haiku-scout` 輪詢確認 Port 就緒。

---

## 一、 Windows 中文路徑避坑守則 (`spring-boot:run` 禁忌)

- **問題根因**：本專案路徑包含非 ASCII 中文字元（`C:\Users\a1083\OneDrive\文件\atria`）。若直接執行 `./mvnw.cmd spring-boot:run`，`spring-boot-maven-plugin` 在 Windows 產生的 `@argfile`（Classpath 暫存參數檔）會因 CP950 / UTF-8 編碼不一致，導致 JVM 找不到主類別而拋出：
  `Error: Could not find or load main class io.github.skr416.atria.AtriaApplication`
- **強制標準解法**：
  **嚴禁使用 `spring-boot:run`**。一律先以 `./mvnw.cmd package -DskipTests` 打包出可執行 Fat JAR，再直接呼叫 JDK 21 的 `java.exe -Dfile.encoding=UTF-8 -jar` 啟動！

---

## 二、 標準啟動四步驟 (4-Step Launch Procedure)

### Step 1：清理殘留佔用 Port（選用，若 Port `8080` / `5173` 已佔用）
在啟動前先檢查 `8080` 與 `5173` 是否已有舊程序佔用：
```bash
netstat -ano | grep -E ":8080|:5173" | grep LISTEN
```
若有舊的背景 Task ID，優先使用 `TaskStop` 關閉。

### Step 2：打包並於背景啟動後端 (`http://localhost:8080`)
1. **打包 Fat JAR**（可交由 `haiku-scout` 或主對話執行）：
   ```bash
   export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"
   cd "C:/Users/a1083/OneDrive/文件/atria/backend" && ./mvnw.cmd package -DskipTests -q
   ```
2. **背景啟動 Spring Boot (`local` profile，內建 H2 記憶體資料庫與種子資料)**：
   使用 `Bash` 工具並設定 **`run_in_background: true`**：
   ```bash
   "C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot/bin/java.exe" -Dfile.encoding=UTF-8 -jar "C:/Users/a1083/OneDrive/文件/atria/backend/target/atria-backend-0.1.0-SNAPSHOT.jar" --spring.profiles.active=local
   ```

### Step 3：背景啟動前端 Vite Dev Server (`http://localhost:5173`)
使用 `Bash` 工具並設定 **`run_in_background: true`**：
```bash
cd "C:/Users/a1083/OneDrive/文件/atria/frontend" && npm run dev -- --port 5173 --host localhost
```

### Step 4：健康探針確認與開啟本機瀏覽器
1. **驗證雙端點就緒**（HTTP 200）：
   ```bash
   curl -s http://localhost:8080/api/v1/health
   curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/public
   ```
2. **呼叫 Windows 隔離桌面專用腳本開啟瀏覽器**：
   依全域 `CLAUDE.md` 規範，嚴禁使用 `start`，一律執行：
   ```bash
   cmd.exe //c "C:\\Users\\a1083\\bin\\open-browser.cmd" "http://localhost:5173/public"
   ```

---

## 三、 預設種子測試帳號清單 (Seeded Demo Accounts)

本地 `local` Profile 啟動時，Flyway `V1`–`V3` 會自動建立以下測試帳號（**統一密碼：`Password123!`**）：

| 帳號 (`username`) | 角色 (`Role`) | 部門 / 時區 / 行事曆 | 驗證重點與可進入大廳 |
| :--- | :--- | :--- | :--- |
| **`admin`** | `SUPER_ADMIN` | 集團總部 (`Asia/Taipei`, `TW`) | 全系統權限（`ALL` 資料範圍），可切換 `/public`、`/portal`、`/admin`（IAM + 差勤後台） |
| **`hr_lead`** | `HR_MANAGER` | 人力資源部 (`Asia/Taipei`, `TW`) | 全公司人資管理（`ALL` 資料範圍），測試產生 `TW`/`JP` 整年行事曆、打卡規則與月結關帳 |
| **`eng_mgr`** | `DEPT_MANAGER` | 研發工程部 (`Asia/Taipei`, `TW`) | 部門與子部門（`DEPT_AND_SUB` 資料範圍），測試部門主管視角 |
| **`emp_alice`** | `EMPLOYEE` | 後端平台組 (`Asia/Taipei`, `TW`) | 僅限本人（`SELF_ONLY`），測試 `/portal` 即時打卡、今日原始打卡軌跡與 7 欄個人月曆 |
| **`emp_locked`** | `EMPLOYEE` | 研發工程部 (`Asia/Taipei`, `TW`) | 預設處於鎖定狀態，供測試 `admin` 於 `/admin/iam/users` 手動解鎖 |

---

## 四、 UI / UX 與 RWD / i18n 必檢驗收清單 (UI & i18n Inspection Checklist)

每次修改前端頁面或準備收尾前，必須對照以下 5 項企業級 UI 標準（亦可派子代理以 `Grep` 靜態掃描）：

1. **PrimeVue `<Button>` 嚴禁漏寫 `:label` 導致中文字直排**：
   - 凡是同時帶有圖示（`<template #icon>` 或 `icon="pi ..."`）與文字的 `<Button>`，文字**必須**透過 `:label="t('...')"` 傳入，嚴禁將文字放在 `<Button>文字</Button>` default slot 中（否則 PrimeVue 4 會誤判為 `.p-button-icon-only` 鎖死 `32px` 寬度導致中文字垂直堆疊）。
2. **1080p 與 2K 螢幕雙解析度頂欄 RWD**：
   - `/public` 與 `/portal` 頂欄容器維持 `width: 100%`（品牌 Logo 固定極左、操作按鈕固定極右）；`/admin` 頂欄按鈕維持 `white-space: nowrap; flex-shrink: 0;`。
3. **品牌主色與狀態色嚴格分離**：
   - 全站主按鈕與品牌識別統一使用皇家藍（`AtriaBluePreset` `severity="primary"`）。
   - 綠色（`severity="success"`）**僅限**用於「正常出勤 (`NORMAL`)、啟用 (`enabled`)、未鎖定 (`OPEN`)」等狀態語意，嚴禁將一般導覽或主操作按鈕設為綠色。
4. **零開發階段術語外洩**：
   - 前台與工作台畫面上**禁止出現** `Phase 2`、`Phase 3a`、`Phase 5 正式啟用` 等開發路線圖詞彙；版本號僅保留於頁尾一處。
5. **100% 三語動態切換覆蓋（零硬編碼下拉選項與代碼裸露）**：
   - 切換右上角 `繁體中文` / `English` / `日本語` 時，所有下拉選單（地區 `regions.*`、時區 `timezones.*`、資料範圍 `iam.dataScopeTypes.*`、日別 `attendance.dayTypes.*`）與角色／權限徽章（`roleCodes.*`、`permissionCodes.*`）皆須同步切換為目標語系，不得殘留中英日混雜之硬編碼字串。

---

## 五、 委派子代理 (`haiku-scout`) 執行打包與預覽健康檢查範本

下次需要快速建置並確認服務健康狀態時，主對話可先以 `run_in_background: true` 啟動前後端，或直接派發以下 `haiku-scout` 任務先完成打包與靜態檢查：

```text
Agent({
  description: "Build backend jar and verify frontend",
  subagent_type: "haiku-scout",
  model: "haiku",
  effort: "low",
  prompt: "請讀取 C:/Users/a1083/OneDrive/文件/atria/docs/LOCAL_PREVIEW_SOP.md，依序執行：1. 在 backend/ 執行 ./mvnw.cmd package -DskipTests 確認 JAR 打包成功；2. 在 frontend/ 執行 npx vue-tsc -b 確認零型別錯誤；3. 檢查是否有任何 PrimeVue <Button> 使用了 #icon 卻未傳入 :label prop。回報執行結果。"
})
```
