# 項目結構

本文檔詳細介紹了小石榴圖文社區項目的目錄結構和文件組織。

## 總體結構

```
小石榴圖文社區/
├── vue3-project/           # 前端項目
├── express-project/        # 後端項目
├── README.md              # 項目主文檔
├── DEPLOYMENT.md          # 部署指南
└── PROJECT_STRUCTURE.md   # 項目結構說明（本文檔）
```

## 前端項目結構（vue3-project/）

```
vue3-project/
├── public/                # 靜態資源目錄
│   └── logo.ico          # 網站圖示
├── src/                  # 原始碼目錄
│   ├── api/              # API接口封裝
│   ├── assets/           # 靜態資源（圖片、樣式等）
│   ├── components/       # 公共元件
│   ├── composables/      # 組合式函數
│   ├── config/           # 配置文件
│   ├── directives/       # 自訂指令
│   ├── router/           # 路由配置
│   ├── stores/           # Pinia狀態管理
│   ├── utils/            # 工具函數
│   ├── views/            # 頁面元件
│   ├── App.vue           # 根元件
│   └── main.js           # 入口文件
├── .env.example          # 環境變數範本
├── index.html            # HTML範本
├── package.json          # 項目配置
├── vite.config.js        # Vite配置
└── README.md             # 前端項目說明
```

### 前端目錄詳細說明

| 目錄/文件 | 說明 | 主要內容 |
|-----------|------|----------|
| `public/` | 靜態資源目錄 | 網站圖示、不需要編譯的靜態文件 |
| `src/api/` | API接口封裝 | HTTP請求封裝、接口定義 |
| `src/assets/` | 靜態資源 | 圖片、字型、樣式文件等 |
| `src/components/` | 公共元件 | 可複用的Vue元件 |
| `src/composables/` | 組合式函數 | Vue 3 Composition API邏輯複用 |
| `src/config/` | 配置文件 | 應用程式配置、常數定義 |
| `src/directives/` | 自訂指令 | Vue自訂指令 |
| `src/router/` | 路由配置 | Vue Router路由定義 |
| `src/stores/` | 狀態管理 | Pinia狀態管理模組 |
| `src/utils/` | 工具函數 | 通用工具函數、輔助方法 |
| `src/views/` | 頁面元件 | 頁面級Vue元件 |
| `App.vue` | 根元件 | 應用程式根元件 |
| `main.js` | 入口文件 | 應用程式入口點 |
| `vite.config.js` | Vite配置 | 建置工具配置 |

## 後端項目結構（express-project/）

```
express-project/
├── config/               # 配置文件目錄
│   ├── config.js        # 主配置文件
│   └── database.js      # 資料庫配置
├── routes/               # 路由文件目錄
│   ├── auth.js          # 認證路由
│   ├── users.js         # 使用者路由
│   ├── posts.js         # 貼文路由
│   ├── comments.js      # 評論路由
│   └── ...              # 其他路由文件
├── middleware/           # 中介軟體目錄
│   ├── auth.js          # 認證中介軟體
│   └── crudFactory.js   # CRUD工廠
├── utils/                # 工具函數目錄
├── scripts/              # 腳本文件目錄
│   ├── init-database.js # 資料庫初始化腳本
│   ├── init-database.sql # SQL初始化腳本
│   ├── generate-data.js # 測試資料產生腳本
│   └── update-sample-images.js # 範例圖片更新腳本
├── app.js               # 應用程式入口文件
├── package.json         # 項目配置
└── .env.example         # 環境變數範本
```

### 後端目錄詳細說明

| 目錄/文件 | 說明 | 主要內容 |
|-----------|------|----------|
| `config/` | 配置文件目錄 | 應用程式配置、資料庫配置 |
| `routes/` | 路由文件目錄 | Express路由定義、API端點 |
| `middleware/` | 中介軟體目錄 | Express中介軟體、認證邏輯 |
| `utils/` | 工具函數目錄 | 通用工具函數、輔助方法 |
| `scripts/` | 腳本文件目錄 | 資料庫初始化、資料產生腳本 |
| `app.js` | 應用程式入口文件 | Express應用程式入口 |

### 路由文件說明

| 路由文件 | 功能 | 主要端點 |
|----------|------|----------|
| `auth.js` | 使用者認證 | 登入、註冊、token驗證 |
| `users.js` | 使用者管理 | 使用者信息CRUD、關注關係 |
| `posts.js` | 貼文管理 | 貼文發布、編輯、刪除、查詢 |
| `comments.js` | 評論管理 | 評論發布、刪除、查詢 |

### 腳本文件說明

| 腳本文件 | 功能 | 使用場景 |
|----------|------|----------|
| `init-database.js` | 資料庫初始化 | 首次部署時建立資料庫結構 |
| `init-database.sql` | SQL初始化腳本 | 直接在MySQL客戶端執行 |
| `generate-data.js` | 測試資料產生 | 開發環境填充測試資料 |
| `update-sample-images.js` | 圖片連結更新 | 更新範例圖片資源 |

## 技術架構

### 前端架構

```
┌─────────────────────────────────────┐
│              Vue 3 App              │
├─────────────────────────────────────┤
│  Views (頁面)  │  Components (元件)  │
├─────────────────────────────────────┤
│  Router (路由) │  Stores (狀態管理)   │
├─────────────────────────────────────┤
│  API (接口)    │  Utils (工具)       │
├─────────────────────────────────────┤
│           Vite (建置工具)            │
└─────────────────────────────────────┘
```

### 後端架構

```
┌─────────────────────────────────────┐
│           Express Server            │
├─────────────────────────────────────┤
│  Routes (路由)  │ Middleware (中介軟體) │
├─────────────────────────────────────┤
│  Config (配置)  │  Utils (工具)      │
├─────────────────────────────────────┤
│           MySQL Database            │
└─────────────────────────────────────┘
```

## 資料流向

```
前端 Vue App
     ↓ HTTP請求
Express 路由
     ↓ 資料處理
中介軟體驗證
     ↓ 資料庫操作
MySQL 資料庫
     ↓ 返回資料
前端狀態更新
     ↓ 視圖渲染
使用者界面展示
```