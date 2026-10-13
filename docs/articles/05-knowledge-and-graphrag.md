# 知识库与图谱增强检索

向量检索解决「语义相近的段落」；领域里还有实体与关系（设备–故障–工单）。DeepStack 在 pgvector 知识库之上，可选叠一层图谱增强。完整设计见 [`../knowledge-graph-rag.md`](../knowledge-graph-rag.md)，摘要见 [`../knowledge-graph-rag-summary.md`](../knowledge-graph-rag-summary.md)。

## 基础 RAG 路径

1. 知识库绑定 **EMBEDDING** 模型（`model_type = 1`）  
2. 文档进对象存储 OSS，切块向量化写入 PG  
3. 对话时按知识库检索，片段进入上下文  

桶：`DEEPSTACK_KB_BUCKET`（及默认桶相关变量）。模型必须在 `ai_model` 里可调通，不能只配 CHAT。

## 图谱什么时候值得开

| 场景 | 更合适的手段 |
| --- | --- |
| FAQ、手册原文问答 | 向量检索即可 |
| 「A 关联哪些 B」「沿关系多跳」 | 图谱或混合 |
| 要解释「为什么召回这段」且含实体链 | 图谱增强有帮助 |

开关：`DEEPSTACK_GRAPH_ENABLED`（默认 `false`）。实现模块是 `graph-age`（Apache AGE）；存储走图谱 SPI，可替换（见 [03](03-port-spi-architecture.md)）。

## 混合检索在做什么（概念）

典型流水线（细节以 knowledge / graph 模块为准）：

1. 用户问题 → 向量召回候选 chunk  
2. （可选）从问题或 chunk 抽实体，在图上扩展邻居 / 路径  
3. 合并、重排后交给模型  

抽取与图写入是异步或批处理友好的设计点；不要在每次对话里默认同步跑重抽取，除非场景明确需要。

## 运营注意

- Embedding 模型与入库时一致，换模型要重建向量。  
- 权限：知识库挂在 Agent 上，避免所有 agent 共享敏感库。  
- 引用展示：控制台 / 接口若返回来源片段，便于人工核对幻觉。

## 和「只接一个向量库 SaaS」的差别

DeepStack 把知识库当 Agent 配置的一部分，和工具、图编排同一控制台运营；图谱是可选增强，不是必选依赖。嵌入宿主时仍用同一套表与 SPI。

下一篇：[HITL](06-hitl-human-in-the-loop.md)。
