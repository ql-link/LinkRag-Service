# 文档体系架构

## 目录职责

| 目录 | 内容 |
| --- | --- |
| `docs/architecture/` | 代码内部结构、模块边界、核心链路 |
| `docs/reference/` | API、数据库、MQ、错误码等契约 |
| `docs/guides/` | 启动、配置、部署、联调 |
| `docs/development/` | 工作流、测试、PR、文档同步 |

## 单一事实来源

- 代码与配置是实现事实来源。
- `docs/db/schema.sql` 是数据库脚本事实来源。
- `docs/reference/*` 是对接契约摘要。
- `.claude/doc-sync-rules.yaml` 是“改代码必须同步哪些文档”的机器规则来源。

## 新文档放置规则

- 描述内部实现：放 `architecture/`
- 描述外部或跨系统契约：放 `reference/`
- 描述使用、部署、联调步骤：放 `guides/`
- 描述协作流程：放 `development/`
- 单次需求产物：放 `.specs/<需求名>/`（本地工作产物，不入库，合并后清理；只 `.specs/README.md` 入库）

## 协作流程文档约定

- `docs/development/spec_as_test_handbook.md`：需求从 brief 到实现的主流程
- `docs/development/branching_and_pr.md`：分支、提交与 PR 约定
- `docs/development/issue_tracking_workflow.md`：Linear 与 GitHub 的 issue 创建与双向同步流程

## 技能治理

- `.ai/skills/README.md`：技能注册表，按类别索引每个 skill 的职责、触发/转交边界与周期复审清单。
- `scripts/check_skills.py`：pre-commit `check-skills` 门禁，校验 skill 的 frontmatter、死引用、技术栈一致性（防 Python/RAG 模板腐化）、孤儿目录。
- `agents-tree-sync` skill：维护 `AGENTS.md`（= `.ai/prompts/project.md`）中的"当前项目结构"树。

当 `.ai/` 下的 skill、项目入口或协作规则发生变化时，应同步更新对应的 `docs/development/*` 文档，避免 Agent 与贡献者看到不同版本的流程说明。
