# Port / SPI：模型、记忆、图谱可替换

嵌入现有系统时，最怕框架把用户、存储、向量库写死。DeepStack 把易变边界收成 Port / SPI：内核依赖接口，实现放在独立模块，用 Spring 装配。

分层习惯见 [`../core-packages.md`](../core-packages.md)。

## 模块怎么切

| 模块 | 职责 |
| --- | --- |
| `ai-deepstack-kernel` | 响应包装、异常、登录 SPI、分页、公共枚举 |
| `ai-deepstack-settings-api` | `SysConfigPort` 只读 SPI |
| `ai-deepstack-aimodel` | 模型配置 CRUD |
| `ai-deepstack-infra` | 按 `model_code` 建 Chat / Embedding 客户端（实现 `ModelPort`） |
| `ai-deepstack-runtime-api` | 图契约：SPI 端口与 `GraphSpec` / `WorkflowState` 等值类型 |
| `ai-deepstack-runtime` | 图编译执行、节点实现、checkpoint |
| `ai-deepstack-graph-api` / `graph-age` | 图谱 SPI / Apache AGE 实现 |
| `ai-deepstack-memory-api` / `memory-pg` | 长期记忆 SPI / PG 实现 |
| `ai-deepstack-storage` | S3 兼容对象存储 |
| `ai-deepstack-starter` | 给宿主工程的聚合依赖 |
| `ai-deepstack-app` | 参考单体（含 Web 控制台静态资源） |

契约与实现分家：`runtime-api` 只放端口与值类型，`runtime` 放编译器执行器与节点。所以只实现端口的能力模块（`infra` / `tool` / `knowledge` / `card`）依赖 `runtime-api` 就够，不必把图执行引擎带进编译期；只有真正编排图的 `agent` / `chat` 依赖 `runtime`。业务模块依赖抽象，不直接 `new` 某一厂商客户端。

## 几个典型扩展点

### 1. 登录用户

kernel 提供 `AppUserProfileLoader`（名称以代码为准）。没有实现时会落演示加载器，方便单体开箱。接公司账号体系时：实现该 SPI，扫到 Spring 容器即可，对话与配额里的「当前用户」跟着走。

### 2. 模型客户端

运营在 `ai_model` 表维护 `base_url` / `api_key` / `api_model_name`。`infra` 按码缓存或重建 Spring AI 客户端。换供应商 = 改表或加一条模型，而不是改业务代码里的 URL 常量。

### 3. 长期记忆

默认可关：`DEEPSTACK_MEMORY_ENABLED=false`。打开后走 memory Port；PG 实现在 `memory-pg`。要换向量库或自研存储，实现 Port 并替换 Bean，chat 侧调用点不用改。

### 4. 知识图谱

`DEEPSTACK_GRAPH_ENABLED` 控制是否启用 AGE 等实现。知识库检索可以叠加图谱增强（见 [05](05-knowledge-and-graphrag.md)）；图谱存储本身是可替换的。

### 5. 系统配置

运行期开关、阈值走 `SysConfigPort`，缺省值集中在 `SysConfigFallback`，控制台「系统设置」按分类改。说明见 [`../sys-config.md`](../sys-config.md)。业务代码读 Port，不散落魔法数字。

## 设计上刻意不做的事

- **不强制多租户**：单租户控制台 + 嵌入宿主；租户隔离若需要，在宿主层做。  
- **不把编排锁死在 YAML 文件**：图定义在库里，便于版本与回滚（F3）。  
- **JSON**：业务侧统一 fastjson2；HTTP API 仍走 Spring 的 Jackson（含 Long 出字符串等约定）。

## 嵌入时怎么用

见 [08-embed-with-starter.md](08-embed-with-starter.md)：依赖 starter、提供 DataSource、执行 bootstrap SQL、扫描 `org.deepstack.ai`、实现用户 SPI。

下一篇：[工具与 MCP](04-tools-and-mcp.md)。
