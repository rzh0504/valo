# 无畏契约赛程（VALORANT Schedule）

基于号角（web.haojiao.cc）无畏契约分区 API 的安卓原生应用：浏览无畏契约赛程与比赛详情。
Kotlin + Jetpack Compose + Material 3，含一个可直接在桌面浏览近期比赛的桌面小组件。

> 仅作本地展示用途，无登录、无上传、无服务端；数据版权归号角（haojiao.cc）所有，请控制请求频率、尊重数据来源权益。

## 功能

- **赛程页**：默认展示「过去 7 天 + 未来 14 天」的比赛，按日期分组、自动定位到今天；顶部箭头可按周平移时间窗；状态下拉筛选（全部 / 未开始 / 已结束）；下拉刷新。比赛卡片显示时间、BO 赛制、对阵双方队标、比分（胜队高亮）、所属赛事与阶段，进行中的比赛有呼吸灯标记。
- **比赛详情页**：赛事与阶段信息、双方队标与大比分、开赛时间与赛制、直播/官网等外部链接（浏览器打开）。
- **桌面小组件**：「无畏契约赛程」小组件列出未来 5 场未开赛/进行中的比赛（无未来赛程时回退展示最近 4 场已结束），点击任意一行直达对应比赛详情。

## 构建

要求：Android Studio（Ladybug 以上）或命令行 JDK 17+ 与 Android SDK（compileSdk 35）。

```bash
# 命令行
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或在 Android Studio 中直接 Open 本目录运行。

## API 集成说明

数据来自 `https://api.haojiao.cc`，共使用 2 个接口：

| 接口 | 用途 |
| --- | --- |
| `POST /wiki/api/v1/match/list_visitor` | 比赛列表（赛程），支持 `start_time`/`end_time`（毫秒）时间窗过滤 |
| `GET /wiki/api/v1/match/battle_detail` | 比赛总览（详情） |

关键实现位于 `app/src/main/java/com/rzh/valo/data/`：

- **签名**（`HaojiaoApi.kt`）：每个请求需携带 5 个自定义头，`x-hj-sign = SHA1(SALT + nonce + timestamp)`，SALT、版本号等为前端公开常量。
- **解密**（`HaojiaoApi.kt`）：`Content-Type: text/plain` 的响应为 AES-192-CBC 加密的 Base64 文本，key 固定 24 字节、IV 取 key 前 16 字节、PKCS7 填充，OkHttp 拦截器中统一解密。
- **比赛状态**：`1 未开始 · 2 进行中 · 3 已结束`；未开赛的对阵 `versus_info` 两侧 camp 可能为 null（队伍待定）。
- **图片**：响应中的相对路径（`/cms/...`）拼接在 `https://files.haojiao.cc` 下。

## 请求频率控制

按"正常浏览量级"设计，具体约束：

- **赛程页**：每个时间窗内存缓存 5 分钟（TTL）+ 单飞锁（同一窗口同时只有一个在途请求）；窗口内比赛超过单页 100 条时才追加翻页（最多 3 页）。日常一次进入仅 1 个请求，下拉刷新才强制刷新。
- **比赛详情**：单条缓存 15 分钟，重复进入不发请求。
- **桌面小组件**：唯一的定时刷新来自 WorkManager 周期任务（1 小时、联网约束、失败指数退避），每次仅 1 个列表请求；系统 `updatePeriodMillis`（30 分钟）触发的重绘只读磁盘快照，不发网络请求。
- **图片**：全部经 Coil 加载，磁盘缓存，队标等静态资源不会重复下载。
- 无轮询、无后台常驻服务；应用不在前台时仅小组件的每小时任务会请求网络。

## 目录结构

```
app/src/main/java/com/rzh/valo/
├── MainActivity.kt            # 单 Activity + Navigation Compose（schedule / match/{id}）
├── ValoApplication.kt         # 应用入口，AppContainer 手动依赖注入
├── data/
│   ├── HaojiaoApi.kt          # OkHttp 客户端：签名头、AES 解密、两个接口
│   ├── Models.kt              # kotlinx.serialization 数据模型
│   ├── MatchRepository.kt     # 缓存 + TTL + 单飞限流
│   └── SnapshotStore.kt       # 磁盘快照（小组件 / 冷启动兜底）
├── ui/
│   ├── components/MatchUi.kt  # 比赛卡片、队标、状态徽章等共享组件
│   ├── schedule/              # 赛程页（ViewModel + Screen）
│   ├── detail/                # 比赛详情页（ViewModel + Screen）
│   └── theme/                 # Material 3 主题（Android 12+ 动态取色）
└── widget/
    ├── ScheduleWidget.kt      # Glance 小组件 + Receiver
    └── WidgetRefreshWorker.kt # 每小时刷新任务
```
