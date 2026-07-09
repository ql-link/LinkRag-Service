# 召回 Session 模块

本文档描述 Java 管理端为前端签发 Python RAG 流式问答 session 的实现。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| session 签发编排 | `RecallSessionServiceImpl` |
| 数据集范围校验 | `RecallScopeResolver` |
| JWT 签名 | `RecallSessionJwtSigner` |
| 配置 | `RecallProperties` |
| HTTP 入口 | `RecallSessionController` |

接口：

```text
POST /api/v1/recall/sessions
```

Java 只负责 Sa-Token 登录态校验、用户状态校验、数据集授权校验、embedding 配置绑定校验和短期 JWT 签发。RAG SSE 流内容由前端携带 token 直连 Python，不经过 Java 代理。

## 签发流程

1. 从登录态取得 userId。
2. 校验 userId 为正数。
3. 查询 `sys_user`，要求用户存在且 `status = 1`。
4. 校验请求中的 datasetIds 均属于当前用户且未软删。
5. 校验每个数据集已有合法 embedding 绑定。
6. 生成包含 userId 和 datasetIds 的短期 JWT。
7. 返回 token、过期秒数和 Python stream URL。

`streamUrl` 由 `recall.session-stream-base-url` 拼接 `/api/v1/rag/stream`。

## 数据集范围

`RecallScopeResolver` 支持两种输入：

| 输入 | 行为 |
| --- | --- |
| 非空 datasetIds | 去重后逐一校验归属，不全属于当前用户则拒绝 |
| 空 datasetIds | 展开为当前用户全部未软删数据集 |

当前 HTTP DTO 要求 datasetIds 非空，因此签发接口不会把空列表作为全库授权写入 token。代码保留空列表展开能力，是内部 resolver 的通用逻辑。

## JWT Claims 约束

Python 端依赖 token 中的用户与数据集范围进行授权判断。Java 签发时应保证：

- `sub` 为正整数用户 ID。
- `dataset_ids` 为已校验的显式 ID 列表。
- token 过期时间来自配置，不在接口层硬编码。

## 修改注意事项

- 不要恢复旧的 Java SSE 中转链路。
- 不要绕过数据集归属校验直接签发 token。
- 修改 Python stream 路径或 JWT claim 时，需要同步 `docs/api/api_contracts.md` 和跨端联调文档。
- 签发不做限流；资源并发控制由 Python RAG 执行端承接。
