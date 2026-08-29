# 康识问诊 · AI 医疗健康知识库智能问答系统（演示）

面向实习演示的本地可运行项目：智能健康问答、健康档案、检查报告解读、后台知识库、科室导诊与用户权限。

**医疗免责声明：本系统全部内容仅供健康科普与技术演示，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。**

## 技术栈

| 层 | 组件 |
| --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus；Editorial Luxury 设计系统（暖奶油纸面、深咖墨色、灰调鼠尾草绿、Fraunces + Geist 可变字体、Double-Bezel 同心容器与 CSS 胶片颗粒，遵循 prefers-reduced-motion/transparency/contrast）；markdown-it + KaTeX；ECharts；SSE 流式对话 |
| 后端框架 | Spring Boot 4 + Sa-Token（JWT 模式，`StpLogicJwtForSimple`） |
| 对话 | Spring AI 2.0 `ChatClient` 流式问答（OpenAI 兼容，默认 DeepSeek，支持图片多模态） |
| RAG | LangChain4j 流水线编排：`DocumentSplitters` 切分 → `EmbeddingStoreContentRetriever` 检索 → `DefaultRetrievalAugmentor` |
| 向量库 | Milvus 2.4（`VectorStoreEmbeddingStore` 适配为 LangChain4j `EmbeddingStore`），不可用时降级内存余弦检索 |
| 持久化 | MyBatis-Plus + MySQL 8（离线可切 H2） |
| 缓存 | Redis（热门问答 + 会话上下文），不可用时降级进程内 TTL 缓存 |
| 会话存储 | Sa-Token 会话落 Redis（`sa-token-redis-template`），登录态跨后端重启保留；Redis 缺失时退回内存 |
| 文档解析 | Apache Tika（PDF/Word/txt）、Hutool |

前端把品牌色、医疗高低语义色与图表分类色严格分层：品牌色不表示状态，高低只由数据驱动；化验数字统一使用等宽字体和参考区间标尺，主信息容器使用外壳 + 内芯的同心圆角结构。

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8（默认数据源）、Docker（用于起 MySQL / Redis / Milvus）
- 可选：Redis 7、Milvus 2.4、OpenAI 兼容大模型与 embedding 接口

Redis、Milvus、大模型、embedding 全部**可缺省**，缺失时自动降级（内存缓存 / 内存向量库 / 模板回答 / 哈希向量）。
只有 MySQL 是默认必需项；完全离线可改用 `h2` profile，见下。

## 一键启动

### 第一步：准备 .env

数据库密码、登录令牌签名密钥、大模型 API Key 都从环境变量读取，仓库里不含任何真实密钥。
复制模板后填入自己的值即可，`.env` 已在 `.gitignore` 中，不会被提交：

```bash
copy .env.example .env      # Windows
cp   .env.example .env      # Linux / macOS
```

至少要改这两项，否则后端会在启动时直接报错：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_PASSWORD` | 数据库密码，`docker compose` 首次启动时按它建用户 |
| `APP_JWT_SECRET` | 登录令牌 HS256 签名密钥，换成一段足够长的随机串 |

`APP_LLM_API_KEY` 不填也能跑，问答会回退模板回答。

`docker compose` 自动读取项目根目录的 `.env`；后端通过 `spring.config.import` 读取同一份文件，
所以从 `backend/` 或项目根目录启动都能取到。

### 第二步：起数据库

Redis 可选，用于缓存热门问答与会话上下文：

```bash
docker compose -p healthkb up -d mysql redis
```

也可以用本机已装的 MySQL，只需建好同名库与账号：

```sql
CREATE DATABASE IF NOT EXISTS healthkb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'healthkb'@'localhost' IDENTIFIED BY '这里填 .env 里的 MYSQL_PASSWORD';
GRANT ALL PRIVILEGES ON healthkb.* TO 'healthkb'@'localhost';
FLUSH PRIVILEGES;
```

> 国内拉不到 Docker Hub 镜像时，可先走镜像源再打回官方名，例如：
> `docker pull docker.1ms.run/library/redis:7-alpine && docker tag docker.1ms.run/library/redis:7-alpine redis:7-alpine`

后端（端口 8080）：

```bash
cd backend
mvn spring-boot:run
```

无 MySQL 的离线场景改用 H2 文件库（数据落在 `backend/data/healthkb`，H2 控制台 `/h2-console`，账号 `sa`，密码见 `H2_PASSWORD`，默认 `healthkb`）：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

> 建表脚本 `db/schema.sql` 是幂等的纯 `CREATE TABLE IF NOT EXISTS`。从旧版本升级请重建库（MySQL 删库重建 / 删除 `backend/data` 目录）。

前端（端口 5173，`/api` 代理到 8080）：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 [http://localhost:5173](http://localhost:5173)

### 演示账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin123! | 管理员 |
| user | User123! | 普通用户 |

## 功能清单

| 模块 | 说明 |
| --- | --- |
| 智能健康问答 | RAG 检索知识库，SSE 流式回答，正文末尾标注出处，医学名词高亮；命中急症关键词时先于模型输出弹出急诊提示；会话支持重命名 |
| 健康档案 | 档案/指标/病史，ECharts 曲线 + 连续超标提醒，AI 建议；默认仅本人可见 |
| 异常提醒中心 | 健康页顶部汇总全部档案的连续超标（需复查）与偶发异常（待观察），支持成员筛选与本地已处理标记 |
| 指标 CSV 导入导出 | 浏览器端解析预览、脏行标注后批量写入（单次 ≤500 条）；导出 CSV 可直接回灌；英文指标名自动归一（如 glucose → 空腹血糖） |
| 报告解读 | Tika 解析 PDF/Word/txt；解析空腹血糖、血压等指标并逐项解读；拍照化验单走多模态转写，解读可导出 PDF |
| 知识库管理 | 启动时同步 WHO / 国家卫健委公开文本；管理员也可再同步或上传 |
| 科室导诊 | 规则 + 向量检索，急诊关键词直达急诊科（规则与问答共用 `EmergencyRules`）；结果页可按位置检索**附近医疗资源** |
| 附近医疗资源 | 高德地图周边检索真实医院/药店（名称、距离、地址、电话），大模型只对真实列表做贴合症状的解释；位置默认用完即走，勾选才保存、可清除；无 key 或断网时降级为科室就医建议 |
| 首页健康新闻 | 启动后与每 6 小时爬取世界卫生组织中文新闻室（正文 + 配图本地化）；断网自动落库内置科普快照；卡片点开是站内图文详情页 |
| 用户系统 | JWT 注册登录、问答历史、收藏、修改密码（改后强制重新登录）；档案隐私权限 |

## 可选配置

### 数据库与缓存

MySQL 是默认数据源，库名与账号默认都是 `healthkb`，密码没有默认值，必须由 `.env` 里的 `MYSQL_PASSWORD` 提供；连接串与用户名可用 `MYSQL_URL` / `MYSQL_USER` 覆盖。

Redis（`spring.data.redis.*`）承担三件事：

- 热门问答结果缓存，TTL `app.rag.cache-ttl-minutes`，默认 10 分钟
- 会话上下文 `ctx:v1:{sessionId}`，TTL `app.rag.context-cache-minutes`，默认 30 分钟
- **Sa-Token 登录态**（`Authorization:login:token:*` / `Authorization:login:session:*`）

前两项连不上时改用带 TTL 的进程内缓存。第三项要注意：JWT 走的是 Simple 风格，token 里只带 `loginId`，登录态本身在 SaSession 里 —— 存内存的话后端一重启所有人被登出，接上 Redis 才能跨重启继续用。

`sa-token-redis-template` 会把 Redis DAO 无条件注册（类上没有任何 `@Conditional`），Redis 一停鉴权就整体 500。所以 `SaTokenDaoConfig` 用 `@Primary` 抢下这个注入点，启动时探一次连接，连不上就退回内存实现 —— 与缓存、向量库的降级策略保持一致。代价是重启后需重新登录，但至少没有 Docker 也能跑起来。

> 该降级只在**启动时**判定一次。运行中途 Redis 掉线不会自动切回内存，鉴权会失败。

### Milvus 向量库

```bash
docker compose -p healthkb up -d milvus
```

不启动 Milvus 时使用进程内余弦检索，功能一致但重启后需重建。切换 embedding 模型导致向量维度变化时，Milvus collection 会自动重建，随后可在后台「知识库」点重新索引灌回数据。

连接参数可用环境变量覆盖：`APP_MILVUS_HOST`（默认 `localhost`，容器内为 `milvus`）、`APP_MILVUS_PORT`（默认 `19530`）、`APP_MILVUS_COLLECTION`（默认 `healthkb_chunks`）；`APP_MILVUS_ENABLED=false` 可整体跳过 Milvus 直用内存向量库。

两条配套的健壮性设计：

- **补账队列**：故障窗口内只写入内存副本的操作会被记入有界队列，Milvus 恢复可达后自动按序补写（删除以墓碑参与排序，避免已删文档复活）；「向量」页会显示待补写条数。
- **启动跳过重建**：启动时比对数据库 chunk 数与向量库条数，一致则不再重跑 embedding，只把已算好的向量批量回灌内存备份（知识库大后能省下可观的启动时间与 API 费用）。判据是条数而非内容 —— 若更新了正文但条数恰好没变，设 `APP_KB_FORCE_REINDEX=true` 或在后台点一次重新索引即可。

### 对话大模型（Spring AI，默认 DeepSeek V4-Flash-Vision）

问答默认走 DeepSeek `deepseek-v4-flash-vision-exp`（多模态 OpenAI 兼容接口），结合知识库摘录用口语回答日常问题；知识库对不上时不会硬套检索结果。flash-vision 支持视觉输入，问答时会把随消息上传的图片（如皮肤科照片、皮疹/皮损等）以 base64 一并发送给模型直接观察识别，未配置或调用失败时回退模板回答。

在 `.env` 里配置：

```properties
APP_LLM_BASE_URL=https://api.deepseek.com
APP_LLM_API_KEY=sk-...
APP_LLM_MODEL=deepseek-v4-flash-vision-exp
```

仓库内不含任何可用 API Key，`APP_LLM_API_KEY` 未配置时问答会回退模板回答。

### 医学知识向量化（可选）

DeepSeek 不提供 embedding 端点，需另配一个 OpenAI 兼容的 embedding 服务才能得到真正有语义的向量：

```properties
APP_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
APP_EMBEDDING_API_KEY=sk-...
APP_EMBEDDING_MODEL=BAAI/bge-m3
```

不配置时回退确定性中文 n-gram 哈希向量（256 维，无需下载模型，无语义能力，仅保证离线可演示）。
当前使用哪一种可在「向量」页或 `GET /api/knowledge/vectors/inspect?q=...` 的 `semantic` 字段看到。

#### 两种检索模式

召回之后的处理按是否有真实向量分两条路，`inspect` 的 `mode` 字段会直接告诉你走的哪条：

| 模式 | 触发条件 | 行为 |
| --- | --- | --- |
| `lexical-filter` | 哈希兜底向量 | 向量分不含语义，字面不沾边的一律丢弃，宁可少召回也不串台 |
| `rerank` | 配了真实 embedding | 综合分 = 向量分×0.7 + 词法特异度×0.3，低于阈值才判定检索不到 |

之所以要分开：词法过滤是按字面子串判断的，而「心梗」和正文里的「心肌梗死」一个字都不重合。
在真实 embedding 下继续硬过滤，等于把语义检索的收益又抵消掉。
`MedicalSynonyms` 另外维护了一张医学别名表（心梗↔心肌梗死、拉肚子↔腹泻等），两种模式都会用到。

重排的两个旋钮在 `app.rag.rerank.*`。**换 embedding 模型后阈值需要重新标定**，
因为不同模型的分数分布不一样 —— 用 `inspect` 看 `combined` 一列，
挑一个能把相关块留下、把无关块挡掉的值：

```
GET /api/knowledge/vectors/inspect?q=心梗后要注意什么

mode = rerank   阈值 = 0.55
  心血管疾病   vec=0.894  lex=0.00  combined=0.626   ← 保留
  心血管疾病   vec=0.832  lex=0.00  combined=0.582   ← 保留
  高血压       vec=0.707  lex=0.00  combined=0.495   ← 挡掉
```

### 接口限流

问答、报告解读、健康建议三个接口都会直连大模型，是本项目唯一直接产生费用的地方，
因此按用户维度做了固定窗口限流。登录与注册是仅有的匿名密码入口，另按**客户端 IP** 单独限流防爆破。
默认值可用环境变量覆盖：

| 动作 | 变量 | 默认 |
| --- | --- | --- |
| 问答 `/api/chat/ask` | `APP_RATE_LIMIT_CHAT` | 20 次 / 分钟 |
| 报告上传 `/api/reports/upload` | `APP_RATE_LIMIT_UPLOAD` | 5 次 / 分钟 |
| 健康建议 `/api/health/advice` | `APP_RATE_LIMIT_ADVICE` | 10 次 / 分钟 |
| 登录/注册 `/api/auth/*`（按 IP） | `APP_RATE_LIMIT_AUTH` | 10 次 / 分钟 |

四个动作共用一个固定窗口，窗口秒数可用 `APP_RATE_LIMIT_WINDOW_SECONDS` 统一调整（默认 60）。

超限返回 HTTP 429。计数优先走 Redis（多实例共享），Redis 不可用时退回进程内计数 ——
此时各实例配额独立、总量会放大到实例数倍，但对「防脚本刷爆」仍然有效。
设为 `0` 表示该项不限流。

### 其他部署开关

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `APP_SEED_DEMO_ACCOUNTS` | 是否创建演示账号（admin/user），对外部署可关 | `true` |
| `APP_CORS_ALLOWED_ORIGINS` | 允许的跨域前端源，逗号分隔多值（直连部署时用） | `http://localhost:5173` |
| `APP_UPLOAD_MAX_BYTES` | 报告上传单文件上限，驱动后端校验与 multipart 配置 | `10MB` |
| `APP_UPLOAD_MAX_REQUEST_BYTES` | 单次上传请求总上限（比文件上限大 2MB 留出表单开销） | `12MB` |
| `APP_KB_ALLOW_NETWORK` | 权威知识库是否允许联网抓取（false 时使用仓库内官方快照） | `true` |
| `APP_NEWS_ENABLED` | 首页健康新闻爬取开关（false 时首页展示已入库内容） | `true` |
| `APP_NEWS_ALLOW_NETWORK` | false 时强制使用内置科普快照，不访问网络（离线演示） | `true` |
| `APP_NEWS_IMAGE_DIR` | 新闻配图本地缓存目录 | `./data/news-images` |

### 权威知识库

默认从下列公开权威源同步（启动时自动执行；后台「知识库」也可再点同步）：

- [世界卫生组织中文实况报道](https://www.who.int/zh/news-room/fact-sheets)：高血压、糖尿病、心血管疾病、流感、健康饮食、身体活动、肥胖、减钠、哮喘、慢阻肺
- [国家卫生健康委](https://www.nhc.gov.cn) 公开文件：心脑血管疾病防治行动实施方案、出生缺陷防治健康教育核心信息

在线拉取成功则入库网页正文并保留原文链接；网络不通时使用仓库内已核对的官方文本快照。旧的演示条目会被替换。关闭联网同步：`set APP_KB_ALLOW_NETWORK=false`。

### 高德地图（附近医疗资源，可选）

在 `.env` 里配置 `AMAP_KEY`（[高德开放平台](https://lbs.amap.com/) 个人开发者免费申请）后，科室导诊结果页底部出现「附近医疗资源」：

- 浏览器定位或手填地址（地理编码）→ 周边检索 3 公里内的医院（优先综合/专科，不足时含社区卫生服务中心）与药房（类目 `090601`）
- 机构名称/距离/地址/电话全部来自高德真实数据；大模型只对这份列表生成贴合症状的建议，禁止编造机构
- 位置默认用完即走；勾选「保存此地址」才写入 `user_location` 表（一人一条，可清除）

不配置 key 或网络不可用时该区块自动降级为科室就医建议文字，不影响导诊本身。

### 报告图片与拍照识别

报告解读对 PDF/Word/txt 走 Tika 解析文本层；**图片（png/jpg/jpeg）则直接交给多模态模型转写**（`LlmClient.extractReportText`，复用问答同一条多模态通道，不引入额外 OCR 引擎）——拍一张化验单上传即可识别出指标并进入「解析 → 解读 → 写入档案」全链路。没有化验单照片时，用仓库里的 `samples/real-lab-cmp.jpg` 即可体验（真实检验报告照片，含 GLUCOSE/BUN/ALT 等英文指标——解析器内置英文指标名 → 中文标准名映射与 mg/dL → mmol/L 单位换算，见 `MetricGuide.normalize`）。

问答里的图片是另一条路：输入区「🖼 图片问诊 / 📷 拍照」上传化验单、药盒或患处照片，模型直接看图回答。

识别失败或未配置多模态模型（`APP_LLM_BASE_URL` / `APP_LLM_API_KEY`）时降级为明确提示，仍可：① 同时提交 `extractedText` 粘贴文字；② 上传文件名含 `demo` 使用内置示例解析；③ 直接上传仓库中的 `samples/demo-report.txt`。

### 演示数据

`scripts/seed-demo-usage.py` 会以真实接口走一遍演示流程（问答/收藏/导诊/档案/指标/病史/建议/报告/知识库），让 `user / User123!` 登录后各页面即有内容（含首页异常指标与迷你趋势）；管理员账号可在「向量检索」页查看库状态。

> 示例化验单 `samples/real-lab-cmp.jpg` 取自 Wikimedia Commons 文件 [CMP report.JPG](https://commons.wikimedia.org/wiki/File:CMP_report.JPG)（作者 Bobjgalindo，CC BY-SA 4.0），仅用于功能演示与解析器测试；项目不含任何真实患者隐私数据。

### 解读结果导出

报告详情页支持 **PDF 导出**：浏览器本地渲染（html2canvas + jsPDF），A4 多页，无需后端参与。界面支持**深色模式**（右上角一键切换，跟随系统偏好）；首页为**健康总览**：异常指标以「需要留心」卡片呈现（带参考范围与「较上次」差值），点击直达档案趋势。

## 容器化部署

基础设施与应用都在 `docker-compose.yml` 里。应用侧放在 `app` profile 下，
默认不随基础设施一起启动：

```bash
# 只起基础设施做本地开发（前后端仍在本机跑）
docker compose up -d mysql redis milvus

# 前后端也一起容器化跑起来
docker compose --profile app up -d --build
```

起来后前端在 `http://localhost:8081`，nginx 把 `/api` 反代到 backend 容器。
SSE 需要关闭代理缓冲，`frontend/nginx.conf` 里已经配好 `proxy_buffering off`，
否则流式回答会被攒到最后一次性吐出。

后端镜像用非 root 运行，且不含任何默认密钥 —— 全部由 compose 从 `.env` 注入。

## 持续集成

`.github/workflows/ci.yml` 在 push 与 PR 时跑三件事：

- 后端 `mvn test`（用 `test/resources/application.yml`，不依赖 `.env` 与任何外部服务）
- 前端 `npm run build`（其中已包含 `vue-tsc --noEmit` 类型检查）
- 密钥自查：`.env` 是否误入库、跟踪文件里是否有形如 `sk-xxx` 的明文 Key

## 主要 API

统一响应：`{ "code": 0, "message": "ok", "data": {} }`，除登录注册外需 `Authorization: Bearer <token>`。
列表接口返回统一分页结构 `{ "records": [...], "total": n, "page": p, "size": s }`（size 上限 100）。

- **认证**：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`、`POST /api/auth/password`（改密码并注销当前会话）
- **问答**：`POST /api/chat/ask`（`text/event-stream`：`meta` / `delta` / `citation` / `done` / `error`；question ≤500 字、最多 4 张图片）；`GET|POST /api/chat/sessions`、`GET|PUT|DELETE /api/chat/sessions/{id}`（重命名 / 删除）、`GET /api/chat/sessions/{id}/messages`、`POST /api/chat/images`、`GET /api/chat/images/{id}`
- **健康档案**：`GET|POST /api/health/profiles`、`PUT|DELETE /api/health/profiles/{id}`、`GET|PUT /api/health/profile`、`GET|POST /api/health/metrics`、`DELETE /api/health/metrics/{id}`、`POST /api/health/metrics/batch`（CSV 批量导入）、`GET /api/health/reference`、`GET /api/health/alerts`、`GET|POST /api/health/histories`、`DELETE /api/health/histories/{id}`、`GET /api/health/trends`、`POST /api/health/advice`
- **首页总览**：`GET /api/home/overview`（统计 + 异常提醒 + 迷你趋势）
- **收藏**：`GET|POST /api/favorites`、`DELETE /api/favorites/{id}`
- **管理端**：`GET /api/admin/health/{userId}`（仅当该用户 `sharedToAdmin=true`）；`GET|POST|DELETE /api/admin/knowledge*`（上传 / 文本录入 / 官方源同步 / 重新索引）
- **知识检索**：`GET /api/knowledge/search`、`GET /api/knowledge/vectors/status`、`GET /api/knowledge/vectors/inspect?q=...`（召回打分与检索模式诊断）、`GET /api/knowledge/highlights`、`GET /api/knowledge/terms`
- **报告**：`POST /api/reports/upload`、`GET /api/reports`、`GET /api/reports/{id}`、`POST /api/reports/{id}/import`
- **导诊**：`POST /api/triage`、`POST /api/triage/nearby`（高德周边检索）、`GET|DELETE /api/triage/location`
- **健康新闻**：`GET /api/news`、`GET /api/news/{id}`、`GET /api/news/{id}/image`

## 目录结构

```
README.md                  本文件（入门、配置、部署）
项目改进纪要.md             改进成果与 5 分钟汇报话术（面向指导老师）
改进清单.md                 全量改进清单（代码审查 → 四轮执行 → 后备池）
.env.example               配置模板（复制为 .env 后填入自己的值，.env 不入库）
docker-compose.yml         基础设施（MySQL/Redis/Milvus）与应用编排
backend/                   Spring Boot 4 + Spring AI + LangChain4j + MyBatis-Plus
frontend/                  Vue 3 + TypeScript + Vite + Element Plus
samples/                   演示素材（真实化验单照片、示例文本报告）
scripts/                   演示数据播种、冒烟/留痕测试脚本
deploy/milvus/             Milvus 离线镜像包与 embedEtcd 配置（国内拉不到镜像时用）
.github/workflows/ci.yml   CI：后端测试 + 前端构建 + 密钥自查
```

## 测试

```bash
cd backend
mvn -q test
cd ../frontend
npx vue-tsc --noEmit
```

当前状态：后端 94 个测试全部通过（`Tests run: 94, Failures: 0, Errors: 0`），前端类型检查与构建通过。

## 已知限制

- 未配置 LLM 时为模板化检索回答，不是真实大模型推理。
- 报告图片识别（拍照化验单）与问答图片一样依赖多模态模型：未配置 `APP_LLM_API_KEY` 时图片报告会给出明确提示并降级到「粘贴文本 / 上传 PDF·Word·txt / demo 示例」；文本类报告不受影响。
- 图片报告的指标拆行以多模态模型转写为准：**两列式/终端截屏类排版**（如实验室系统绿屏截图）偶有行对齐错位（个别英文名落入单位位），核心指标（血糖、转氨酶等）已验证对齐；解析器内置英文名→中文映射、mg/dL→mmol/L 换算、行号剥离与单位白名单，普通横版打印/拍照报告效果最佳。
- 未配置 `app.embedding.*` 时向量为哈希嵌入，适合演示语料召回，不适合大规模语义检索；此时检索走 `lexical-filter` 模式，同义表述的召回能力有限。
- `app.rag.rerank.min-score` 的默认值 0.55 是按 cosine 相关度 0~1 给的起点，不是对某个具体模型标定过的最优值。
- 启动是否重建向量按「DB chunk 数 = 向量库条数」判定，发现不了改内容不改条数的更新（用 `APP_KB_FORCE_REINDEX=true` 或后台重新索引兜底）。
- 上传校验基于扩展名白名单 + 文件头签名，文本类以「UTF-8 可解码且无 NUL」为准，UTF-16 编码的 txt 会被拒；图片 MIME 由检出类型推导，不信任客户端声明。
- 问答的图片识别完全交给多模态模型（flash-vision 直接观察图片），已移除对本机 Tesseract 的依赖；未配置多模态模型时图片问诊不可用。
- 未启动 Milvus 时退化为内存向量库；接口抽象保证开箱可用。
- 知识库使用 WHO / 国家卫健委等公开网页或官方快照，不是医院电子病历或付费指南全文库。
