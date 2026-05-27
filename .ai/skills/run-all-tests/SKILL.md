---
name: run-all-tests
description: 运行 toLink-Service 全量 Maven 测试并报告结果。
when_to_use: "运行所有测试、跑全量测试、mvn test 并告诉我结果。"
---

# Run All Tests

## 命令

默认执行：

```bash
mvn test
```

## 规则

- 不自动修改代码，除非用户明确要求修复。
- 若环境依赖导致无法完成，说明是测试失败还是环境阻塞。
- 输出执行命令、范围、通过/失败/错误/跳过数、失败摘要和结论。
