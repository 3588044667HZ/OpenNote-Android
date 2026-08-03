
# Open Note
 ## Here also has English README.md https://github.com/3588044667HZ/OpenNote-Android/blob/main/README_EN.md
原生 Android 便签应用，支持富文本编辑、护眼皮肤、暗色模式、分享为图片等功能。基于 Jetpack Compose + WebView 构建。

## 功能特性

- **富文本编辑** — 标题+正文双编辑器，WebView contentEditable 实现，Markdown ↔ HTML 互转
- **护眼皮肤** — 7 种颜色皮肤，暖黄纸张质感背景，CSS 变量主题系统
- **暗色模式** — 跟随系统自动切换，渲染时实时判断，无广播监听
- **分享为图片** — WebView 截图 → 预览 → 保存相册 / 系统分享，支持自定义水印
- **笔记本管理** — 笔记本增删改查，服务器同步
- **回收站** — 软删除 + 恢复 + 永久删除
- **离线优先** — Room 本地数据库，网络恢复后增量同步
- **JWT 认证** — Token 自动刷新拦截器，密码明文(内网环境)

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 编辑器 | Android WebView (contentEditable) + JavaScript Bridge |
| 数据库 | Room（离线优先） |
| 网络 | Retrofit + OkHttp（JWT 拦截器） |
| 依赖注入 | Hilt |
| 状态管理 | Kotlin StateFlow / DataStore |
| 图片导出 | View → Canvas → Bitmap 管道 |

## 架构

```
UI 层 (Compose)
  ├── LoginActivity      （登录）
  ├── MainActivity        （笔记/回收站/设置 三Tab）
  ├── NoteEditorActivity  （WebView 编辑器）
  └── SharePreviewActivity（分享预览）

ViewModel 层
  ├── Auth / NoteList / NoteEditor / Trash / Settings
  └── SkinViewModel

Data 层
  ├── Room (NoteDao, FolderDao)
  ├── DataStore (认证、皮肤、服务器配置)
  ├── Retrofit API (笔记、认证、笔记本、分享设置)
  └── Repository (离线优先模式)

Share 模块
  ├── ContentCaptureEngine  → WebView 截图
  ├── ShareImageComposer    → View 树 → Bitmap
  └── ImageExporter         → 相册 / Intent 分享
```

## 快速开始

### 环境要求
- Android Studio Hedgehog 或更新版本
- JDK 17
- Min SDK 26, Target SDK 34

### 构建
```bash
git clone <repo-url>
cd android-app
./gradlew assembleDebug
```

### 启动服务端
应用连接 REST API 服务器。默认地址：`http://10.0.2.2:5000/api/`（模拟器）。

```bash
cd mock-server
pip install flask flask-cors
python server.py
```

登录页右上角齿轮图标或设置页中可修改服务器地址。

## 项目结构

```
android-app/
├── app/src/main/java/com/open/note/
│   ├── MainActivity.kt
│   ├── OpenNoteApp.kt
│   ├── di/AppModule.kt          （Hilt 依赖注入）
│   ├── data/
│   │   ├── local/                （Room 数据库、DataStore）
│   │   ├── remote/               （Retrofit API、DTO、拦截器）
│   │   ├── repository/           （Auth、Note、Sync）
│   │   └── skin/                 （皮肤模型、WebView CSS）
│   ├── share/                    （截图引擎、图片合成、导出器）
│   └── ui/
│       ├── login/                （登录）
│       ├── notes/                （笔记列表）
│       ├── editor/               （编辑器）
│       ├── trash/                （回收站）
│       └── settings/             （设置）
├── app/src/main/assets/
│   ├── editor.html               （双区域 contentEditable 编辑器）
│   └── editor-core.js            （编辑器 JS：Markdown、Bridge、焦点追踪）
└── mock-server/                  （Flask API Mock）
```

## API 接口

详见 [API.md](./API.md)：
- `POST /api/auth/login|register|refresh|logout`
- `GET|POST|PUT|DELETE /api/notes`
- `GET|POST|PUT|DELETE /api/notebooks`
- `GET|PUT /api/settings/share`
- `GET /api/notes/sync`（增量同步）

## 许可证

MIT
