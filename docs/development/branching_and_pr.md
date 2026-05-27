# Branching & PR Workflow

## 分支

| 类型 | 用途 |
| --- | --- |
| `feature/<topic>` | 新功能 |
| `refactor/<topic>` | 重构 |
| `fix/<topic>` | 缺陷修复 |
| `docs/<topic>` | 文档与流程调整 |
| `chore/<topic>` | 工具、CI、依赖 |

## 提交前自检

```bash
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --staged
mvn test
```

## PR 内容

PR 描述至少包含：

- Summary：改动目标
- Changes：主要代码、文档、配置变化
- Tests：实际运行命令和结果
- Risks：兼容性、数据、MQ、缓存、OSS、部署风险
