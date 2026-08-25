# 康识问诊 · AI 医疗健康知识库智能问答系统（演示）

面向实习演示的本地可运行项目：智能健康问答、健康档案、检查报告解读、后台知识库、科室导诊与用户权限。

**医疗免责声明：本系统全部内容仅供健康科普与技术演示，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。**

## 技术栈

| 层 | 组件 |
| --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus；markdown-it + KaTeX（Markdown/公式渲染、医学术语高亮）；ECharts（健康数据可视化）；SSE 流式对话 |
| 后端框架 | Spring Boot 4 + Sa-Token（JWT 模式，`StpLogicJwtForSimple`） |
| 对话 | Spring AI 2.0 `ChatClient` 流式问答（OpenAI 兼容，默认 DeepSeek，支持图片多模态） |
| RAG | LangChain4j 流水线编排：`DocumentSplitters` 切分 → `EmbeddingStoreContentRetriever` 检索 → `DefaultRetrievalAugmentor` |
| 向量库 | Milvus 2.4（`VectorStoreEmbeddingStore` 适配为 LangChain4j `EmbeddingStore`），不可用时降级内存余弦检索 |
| 持久化 | MyBatis-Plus + MySQL 8（离线可切 H2） |
| 缓存 | Redis（热门问答 + 会话上下文），不可用时降级进程内 TTL 缓存 |
| 会话存储 | Sa-Token 会话落 Redis（`sa-token-redis-template`），登录态跨后端重启保留；Redis 缺失时退回内存 |
| 文档解析 | Apache Tika（PDF/Word/txt）、Hutool |

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
| 智能健康问答 | RAG 检索知识库，SSE 流式回答，引用卡片，医学名词高亮；命中急症关键词时先于模型输出弹出急诊提示 |
| 健康档案 | 档案/指标/病史，ECharts 趋势，AI 建议；默认仅本人可见 |
| 报告解读 | Tika 解析 PDF/Word/txt；解析空腹血糖、血压等指标并逐项解读 |
| 知识库管理 | 启动时同步 WHO / 国家卫健委公开文本；管理员也可再同步或上传 |
| 科室导诊 | 规则 + 向量检索，急诊关键词直达急诊科（规则与问答共用 `EmergencyRules`） |
| 用户系统 | JWT 注册登录、问答历史、收藏；档案隐私权限 |

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

### 权威知识库

默认从下列公开权威源同步（启动时自动执行；后台「知识库」也可再点同步）：

- [世界卫生组织中文实况报道](https://www.who.int/zh/news-room/fact-sheets)：高血压、糖尿病、心血管疾病、流感、健康饮食、身体活动、肥胖、减钠、哮喘、慢阻肺
- [国家卫生健康委](https://www.nhc.gov.cn) 公开文件：心脑血管疾病防治行动实施方案、出生缺陷防治健康教育核心信息

在线拉取成功则入库网页正文并保留原文链接；网络不通时使用仓库内已核对的官方文本快照。旧的演示条目会被替换。关闭联网同步：`set APP_KB_ALLOW_NETWORK=false`。

### 报告图片

未安装 Tesseract 时图片无法 OCR。可同时提交 `extractedText`，或将文件名包含 `demo` 以使用内置示例解析。也可直接上传仓库中的 `samples/demo-report.txt`。

## 主要 API

统一响应：`{ "code": 0, "message": "ok", "data": {} }`，除登录注册外需 `Authorization: Bearer <token>`。

- `POST /api/auth/register` `POST /api/auth/login` `GET /api/auth/me`
- `POST /api/chat/ask`（`text/event-stream`：`meta` / `delta` / `citation` / `done` / `error`）
- `GET/POST /api/health/profile|metrics|histories` `POST /api/health/advice`
- `GET /api/admin/health/{userId}`（仅当该用户 `sharedToAdmin=true`）
- `POST /api/reports/upload` `GET /api/reports` `GET /api/reports/{id}`
- `GET/POST/DELETE /api/admin/knowledge*`
- `POST /api/triage`

## 目录结构

```
README.md
docker-compose.yml
samples/demo-report.txt
backend/          Spring Boot 4 + Spring AI + LangChain4j + MyBatis-Plus
frontend/         Vue 3 + TypeScript + Vite + Element Plus
```

## 测试

```bash
cd backend
mvn -q test
cd ../frontend
npx vue-tsc --noEmit
```

## 已知限制

- 未配置 LLM 时为模板化检索回答，不是真实大模型推理。
- 未配置 `app.embedding.*` 时向量为哈希嵌入，适合演示语料召回，不适合大规模语义检索；此时检索走 `lexical-filter` 模式，同义表述的召回能力有限。
- `app.rag.rerank.min-score` 的默认值 0.55 是按 cosine 相关度 0~1 给的起点，不是对某个具体模型标定过的最优值。
- 问答支持图片视觉识别：默认 flash-vision 会直接观察上传图片；本机 Tesseract OCR 仅作为文字补充，缺失时仍可看图作答。
- 未启动 Milvus 时退化为内存向量库；接口抽象保证开箱可用。
- 知识库使用 WHO / 国家卫健委等公开网页或官方快照，不是医院电子病历或付费指南全文库。
