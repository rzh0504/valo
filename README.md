# 无畏契约赛程（VALORANT Schedule）

基于号角（web.haojiao.cc）无畏契约分区 API 的安卓原生应用：浏览无畏契约赛程与比赛详情。
Kotlin + Jetpack Compose + Material 3（Expressive 风格），含可直接在桌面浏览近期比赛的桌面小组件。

> 仅作本地展示用途，无登录、无上传、无服务端；数据版权归号角（haojiao.cc）所有，请控制请求频率、尊重数据来源权益。

## 功能

- **底部导航**：今天 / 赛程 / 设置 三个页签，状态独立保存。
- **今天（首页）**：当日赛程，Expressive 大字日期头部 + 场次汇总胶囊；比赛按「进行中（呼吸灯强调卡）→ 未开始 → 已结束」分组展示；下拉刷新。
- **赛程**：默认「过去 7 天 + 未来 14 天」按日期分组、自动定位到今天；顶栏箭头按周平移时间窗；状态筛选（全部/未开始/已结束）；下拉刷新。
- **比赛详情**：
  - 头部：赛事/阶段/赛制、双方队标与大比分（胜队高亮）、时间；
  - **地图小局**（已开赛比赛自动加载）：地图胶囊切换，每图展示总比分与攻/防半场比分、逐回合胜负时间轴、双方选手本图数据（英雄头像、K/D/A、ACS）；
  - 直播/官网等外部链接（浏览器打开）。
- **设置**：主题模式（跟随系统/浅色/深色，DataStore 持久化）、Material You 动态取色开关、小组件立即刷新、关于与限流说明。
- **桌面小组件**：「无畏契约赛程」列出未来 5 场未开赛/进行中的比赛（无未来赛程时回退展示最近完赛），点击任意一行直达对应比赛详情。

## 构建

要求：Android Studio（Ladybug 以上）或命令行 JDK 17+ 与 Android SDK（compileSdk 37）。

```bash
# 命令行
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或在 Android Studio 中直接 Open 本目录运行。

## API 集成说明

数据来自 `https://api.haojiao.cc`，共使用 3 个接口：

| 接口 | 用途 |
| --- | --- |
| `POST /wiki/api/v1/match/list_visitor` | 比赛列表（赛程），支持 `start_time`/`end_time`（毫秒）时间窗过滤 |
| `GET /wiki/api/v1/match/battle_detail` | 比赛总览（data 为 `{radar_config, match, additional, game}`） |
| `GET /wiki/api/v1/match/get_valorant_round` | 逐回合明细：每图小局比分、攻防半场、回合序列、选手数据；未开赛返回空 |

关键实现位于 `app/src/main/java/com/rzh/valo/data/`：

- **签名**（`HaojiaoApi.kt`）：每个请求需携带 5 个自定义头，`x-hj-sign = SHA1(SALT + nonce + timestamp)`，SALT、版本号等为前端公开常量。
- **解密**（`HaojiaoApi.kt`）：`Content-Type: text/plain` 的响应为 AES-192-CBC 加密的 Base64 文本，key 固定 24 字节、IV 取 key 前 16 字节、PKCS7 填充，OkHttp 拦截器中统一解密。
- **比赛状态**：`1 未开始 · 2 进行中 · 3 已结束`；未开赛的对阵 `versus_info` 两侧 camp 可能为 null（队伍待定）。
- **图片**：响应中的相对路径（`/cms/...`）拼接在 `https://files.haojiao.cc` 下。

## Material 3 Expressive 说明

material3 `1.4.0` stable 将 `MaterialExpressiveTheme` 等 Expressive API 转为 internal（公开版在 1.5.0-alpha 线，但其依赖 Compose 1.12 → 要求 AGP 9.1 全套迁移）。因此本项目在 1.4.0 稳定 API 之上手工实现 Expressive 视觉：更圆润的形状层级（24/32/40dp）、大字重展示级排版、tonal 色块卡片、胶囊形徽章、按压缩放回弹动效、自定义三点律动加载指示、SegmentedButton 分段控件、AnimatedContent 切换动画。

## 请求频率控制

按"正常浏览量级"设计，具体约束：

- **赛程页/首页**：每个时间窗内存缓存 5 分钟（TTL）+ 单飞锁（同一窗口同时只有一个在途请求）；窗口内比赛超过单页 100 条时才追加翻页（最多 3 页）。日常一次进入仅 1 个请求，下拉刷新才强制刷新。
- **比赛详情**：总览与逐回合明细各 1 个请求、分别缓存 15 分钟，重复进入不发请求。
- **桌面小组件**：唯一的定时刷新来自 WorkManager 周期任务（1 小时、联网约束、失败指数退避），每次仅 1 个列表请求；系统 `updatePeriodMillis`（30 分钟）触发的重绘只读磁盘快照，不发网络请求；设置页"立即刷新"为手动补充手段。
- **图片**：全部经 Coil 加载，磁盘缓存，队标/地图图等静态资源不会重复下载。
- 无轮询、无后台常驻服务；应用不在前台时仅小组件的每小时任务会请求网络。

## 目录结构

```
app/src/main/java/com/rzh/valo/
├── MainActivity.kt            # 单 Activity：底部导航（今天/赛程/设置）+ 比赛详情路由 + 小组件深链
├── ValoApplication.kt         # 应用入口，AppContainer 手动依赖注入
├── data/
│   ├── HaojiaoApi.kt          # OkHttp 客户端：签名头、AES 解密、三个接口
│   ├── Models.kt              # kotlinx.serialization 数据模型（赛程 / 对阵 / 地图小局 / 选手）
│   ├── MatchRepository.kt     # 缓存 + TTL + 单飞限流
│   ├── SnapshotStore.kt       # 磁盘快照（小组件 / 冷启动兜底）
│   └── SettingsStore.kt       # DataStore 设置（主题模式、动态取色）
├── ui/
│   ├── components/MatchUi.kt  # 比赛卡片、队标、状态徽章、按压回弹、加载动效等共享组件
│   ├── home/                  # 今天页（ViewModel + Screen）
│   ├── schedule/              # 赛程页（ViewModel + Screen）
│   ├── detail/                # 比赛详情页（ViewModel + Screen）
│   ├── settings/              # 设置页（ViewModel + Screen）
│   └── theme/                 # Material 3 主题（Expressive 形状、动态取色）
└── widget/
    ├── ScheduleWidget.kt      # Glance 小组件 + Receiver
    └── WidgetRefreshWorker.kt # 每小时刷新任务
```
