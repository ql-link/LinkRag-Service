# Contributing — toLink-Service

贡献者入口。本文件是**总纲 + 路由**：流程总览见此，完整约定在 [CLAUDE.md](../CLAUDE.md) 与对应 skill。

## 开发流程：Spec-as-Test

新需求按 `brief.md → acceptance.feature → technical_design.md → Code + Tests` 推进，每阶段有门禁（前一个冻结才进下一个）。

- 流程与阶段门禁详见 [CLAUDE.md「二、Spec-as-Test」](../CLAUDE.md)
- 产物放 `.specs/<需求名>/`（本地工作产物，不进 repo，合并后清理）
- skill：`brief-generator` → `acceptance-generator` → `technical-design` → `implementation-execution`

## 分支与发布

- `master` 是稳定发布分支，也是所有新开发分支的唯一基线；`dev` 只承担开发环境集成、构建和验收。本仓库默认分支为 `master`，文档和 CI 不使用 `main`。
- 日常开发分支使用 `feature/`、`refactor/`、`chore/`、`fix/`、`docs/`、`hotfix/` + 英文 kebab-case，统一从最新 `master` 拉出。
- 实现完成后，先以当前分支提 PR 合入 `dev`；等待开发环境构建完成并通过真实环境验收。
- 验收通过后保留当前分支，以同一分支、同一 HEAD SHA 再提 PR 合入 `master` 发布；禁止将 `dev` 整体合入 `master`。
- 开发环境验收后若新增提交，必须重新走 `分支 → dev → 构建/验收` 门禁，再更新或创建 `master` PR。
- 当前分支到 `master` 的发布 PR 必须使用普通 merge commit，禁止 squash merge；PR 描述必须列出开发环境构建、验收证据、数据库/配置/契约变更和风险。
- 发布 PR 合入 `master` 后，在 `master` 的发布 merge commit 上打版本 tag。
- 提交信息用 Conventional Commits（`feat` / `fix` / `refactor` / `docs` / `chore`）
- 发 PR 用 `branch-pr-workflow` skill（含分支命名约定与 PR 描述模板）

## 测试

- 全量 `mvn test`；单模块 `mvn -pl <module> test`
- 规范见 [internals/testing.md](internals/testing.md)；写/补测试用 `auto-test`，跑全量用 `run-all-tests`
- 对已启动服务做 curl 黑盒接口测试（含边界、直连库造数）用 `curl-api-test`
- MQ 中间件迁移须同时覆盖拓扑声明、publisher confirm、消费者委托与 trace header 恢复，并在开发环境做真实 broker 黑盒验证

## 文档同步（机器强制）

高风险改动必须同步对应文档，否则 pre-commit 阻断：

- API / DTO 变更 → `docs/api/api_contracts.md`
- Entity / 数据库脚本 → `docs/api/mysql_schema.md` + `scripts/db/init.sql` + `link-api/src/main/resources/schema.sql`
- MQ 消息 / 消费者 → `docs/api/mq_contracts.md`、`docs/internals/mq_module.md`
- 规则在 `.claude/doc-sync-rules.yaml`，由 pre-commit 三道门禁强制

提交前自检：

```bash
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
python3 scripts/check_skills.py
```

## Skill 与项目结构

- 改 / 增 skill 后跑 `check_skills.py`；skill 清单与治理见 [.ai/skills/README.md](../.ai/skills/README.md)
- 修改跨端 MQ 消息、中间件选型、重试/DLT 或缓存补偿契约时使用 `mq-middleware` skill，并同步 `docs/api/mq_contracts.md` 与 `docs/internals/mq_module.md`
- 当前主模块边界见 [internals/project_structure.md](internals/project_structure.md)；横向可观测能力独立在 `link-observability`，不要再把 trace/access/audit 新代码塞回 `link-core`
- 非 docs 目录结构变动（新增模块、迁脚本等）同步 `AGENTS.md` 的结构树（`agents-tree-sync` skill）
