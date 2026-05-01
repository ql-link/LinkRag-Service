# 模块开发文件清单说明

## 1. 目录定位

`docs/模块开发文档/` 用于按模块归档需求、设计、实现与测试交付文档。

这里的归档单位是“模块开发过程”，每个模块下再按 `一期 / 二期 / 三期` 拆分阶段文件夹。

## 2. 命名规则

模块目录命名格式：

```text
<模块名称>
```

示例：

```text
文件上传与解析
用户管理
LLM配置管理
```

说明：

- 使用中文命名，直接表达模块业务含义

阶段子目录命名：

```text
一期
二期
三期
```

是否需要拆 `一期 / 二期`，由需求分析阶段先判断，再由用户确认。

## 3. 目录结构

推荐结构如下：

```text
docs/模块开发文档/
└── <模块名称>/
    ├── 一期/
    │   ├── feature_info.md
    │   ├── requirement.md
    │   ├── technical_design.md
    │   ├── implementation_report.md
    │   └── testing_delivery.md
    └── 二期/
```

每个期次目录默认候选文件如下：

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

实际需要哪些文件，仍由复杂度等级决定：

- `L1`：`feature_info.md`、`requirement.md`
- `L2`：`feature_info.md`、`requirement.md`、`technical_design.md`、`testing_delivery.md`
- `L3`：全部文件

## 4. 推荐使用方式

1. 先由 AI 完成需求初判、复杂度建议和是否拆 `一期/二期` 的建议
2. 用户确认复杂度与期次拆分方式
3. 创建模块目录
4. 在模块目录下创建当前期次子目录
5. 优先写当前期次的 `feature_info.md`
6. 再按阶段产出其余文档

## 5. 模板位置

各阶段模板与对应 skill 放在一起维护，示例：

- `feature_info.template.md`：`.agents/skills/project-bootstrap/`
- `PRD.template.md`：`.agents/skills/requirement-analysis/`
- `technical_design.template.md`：`.agents/skills/technical-design/`
- `implementation_report.template.md`：`.agents/skills/implementation-execution/`
- `testing_delivery.template.md`：`.agents/skills/test-and-delivery/`
