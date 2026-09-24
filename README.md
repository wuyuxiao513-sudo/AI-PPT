# PresentMind — AI 智能 PPT 生成器教程项目

输入一句主题、粘贴长文本，或上传 PDF / Word / Markdown / TXT 文档，系统会先生成可修改的大纲，再通过多个 AgentScope 智能体并发生成页面，最终导出由原生文本框和形状组成、可继续编辑的 `.pptx` 文件。

## 功能

- 三种内容入口：主题、长文本、文档上传
- Apache Tika 解析 PDF、DOC、DOCX、Markdown、TXT
- 大纲先行：标题、页面意图、顺序、版式均可修改
- AgentScope Java 多智能体：策划、内容、视觉设计、审校
- 固定线程池按页并发生成，前端实时展示进度
- Apache POI 生成 16:9 原生 PPTX（非截图式导出）
- MySQL 保存项目、大纲、页面内容和生成状态
- 未配置模型时自动启用本地演示模式，完整流程仍然可用
- Docker Compose 一键启动

## 架构

```text
Vue 3 / Vite
      │ REST + multipart / 进度轮询
Spring Boot 3
      ├─ Tika 文档解析
      ├─ AgentScope Planner Agent → 可编辑大纲
      ├─ Writer Agents ─┐
      ├─ Designer Agent ├─ 按页并发 → Reviewer Agent
      ├─ MySQL          ┘
      └─ Apache POI → editable.pptx
```

## 快速开始

### Docker（推荐）

复制环境变量并填写阿里云百炼或其他 OpenAI 兼容服务的密钥：

```bash
cp .env.example .env
docker compose up --build
```

浏览器打开 <http://localhost:8080>。不填写 `AI_API_KEY` 也可以体验本地演示生成。

### 本地开发

要求：JDK 17+、Maven 3.9+、Node.js 20+、MySQL 8+。

```sql
CREATE DATABASE ai_ppt CHARACTER SET utf8mb4;
CREATE USER 'aippt'@'%' IDENTIFIED BY 'aippt_password';
GRANT ALL ON ai_ppt.* TO 'aippt'@'%';
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

开发地址为 <http://localhost:5173>，Vite 会将 `/api` 代理到后端。

## 模型配置

默认面向阿里云百炼 OpenAI 兼容接口：

```bash
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_API_KEY=sk-...
AI_MODEL=qwen-plus
GENERATION_PARALLELISM=4
```

也可以换成任何兼容 OpenAI Chat Completions 的服务。密钥仅从环境变量读取，不会返回前端或写入数据库。

## 关键 API

| 方法 | 地址 | 用途 |
|---|---|---|
| `POST` | `/api/presentations` | 提交主题、文本或文件并生成大纲 |
| `PUT` | `/api/presentations/{id}/outline` | 保存修改后的大纲 |
| `POST` | `/api/presentations/{id}/generate` | 异步并发生成页面 |
| `POST` | `/api/presentations/{id}/cancel` | 停止正在生成的项目，保留已完成页面 |
| `GET` | `/api/presentations/{id}` | 查询页面与进度 |
| `GET` | `/api/presentations/{id}/download` | 下载原生 PPTX |
| `DELETE` | `/api/presentations/{id}` | 删除项目；如果正在生成，先取消任务 |

## 教程：一次生成是怎样完成的

1. `DocumentTextService` 使用 Tika 将上传文件转为纯文本，并限制扩展名和文本长度。
2. `AgentScopePptService.plan` 创建策划智能体，输出结构化大纲；调用失败会安全回退到本地大纲。
3. 用户在 Vue 页面调整大纲后，`PresentationService.startGeneration` 将每页提交给有界线程池，并记录可取消的任务。
4. 每个任务由内容智能体写初稿、审校智能体压缩并核对，再由设计智能体选择版式。
5. 前端每秒读取项目状态，所有页面完成后开放下载。
6. `PptxExportService` 使用 Apache POI 创建文本框、项目符号和几何形状，因此下载后可直接编辑。

## 测试与构建

```bash
cd backend && mvn test
cd ../frontend && npm run build
```

后端测试使用 H2 的 MySQL 兼容模式，不依赖本地 MySQL 或模型密钥。

## 目录

```text
backend/                  Spring Boot API、多智能体编排、PPTX 导出
frontend/                 Vue 3 单页工作台
docker-compose.yml        应用 + MySQL
Dockerfile                前后端多阶段生产构建
.env.example              可复制的环境变量模板
```

## 生产注意事项

- 为接口增加身份认证、租户隔离、限流和文件病毒扫描。
- 多实例部署时，把异步任务迁移到消息队列，并使用 Redis 或任务表做分布式进度管理。
- 对模型输入做数据分级与脱敏；不要在日志中记录原始文档或 API Key。
- 当前主题刻意保持简洁，适合作为教程继续扩展模板库、图片检索、图表和品牌资产。

## License

MIT
