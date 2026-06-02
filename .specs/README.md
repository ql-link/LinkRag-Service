# .specs — feature 工作产物（本地，不入库）

本目录存放 Spec-as-Test 工作流的临时产物：

```
.specs/<需求名>/
├── brief.md                需求契约：为什么/做什么/不做/风险
├── acceptance.feature      验收契约：什么算做对（可断言）
├── technical_design.md     实现契约：在哪改/怎么改/怎么测
├── feature_info.md         阶段状态跟踪
└── implementation_report.md 实现交付记录
```

## 约定（与 `.gitignore` 对齐）

- **只有本文件 `.specs/README.md` 入库**；其余全部为本地工作产物，合并后清理。
- 产物的长期可追溯性靠 git 历史与 PR，主干 `docs/` 不再保留按需求命名的目录。
- 新需求用 `brief-generator` 等 skill 在此生成产物。
