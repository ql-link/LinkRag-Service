---
name: mysql-ddl-conventions
description: MySQL 建表与字段规范（面向 Java 管理端业务：用户、LLM 配置、数据集、知识文件、解析任务）。统一命名、索引、字段类型、时间戳、引擎字符集与注释要求，便于研发与 DBA 评审落地。
when_to_use: "当用户要求设计新的数据库表、编写 DDL 语句、补充字段约束、添加索引、优化表结构或统一建表规范时激活。触发示例：'帮我设计一个XXX表'、'写个建表语句'、'这个表需要加什么索引'、'DDL怎么写'"
---

# MySQL 建表规范

## 0. 必读

- `docs/api/mysql_schema.md`：现有表结构与字段说明
- `scripts/db/schema.sql`：当前 DDL 权威来源，新表风格与现有表保持一致

## 1. 命名规范

### 1.1 表名与字段名

- 必须统一使用小写字母，单词之间使用下划线 `_` 分隔（snake_case），例如 `file_type`、`task_status`。
- 表名应为名词，建议用单数形式（代表业务实体），避免不必要的复数化。
- 严禁使用拼音或中英文混搭命名。

### 1.2 索引命名

- 主键索引：建表时直接指定 `PRIMARY KEY`，无需额外命名。
- 普通索引：以 `idx_` 开头，后接字段名，例如 `idx_dataset_id`。
- 唯一索引：以 `uk_` 开头，后接字段名，例如 `uk_user_name`。

### 1.3 索引设计原则

- 必须根据业务访问路径为"高频查询/高选择性"的字段建立索引，例如：
  - 任务查询：`knowledge_file_id`、`status`、`created_at`
  - 用户/租户隔离：`user_id`、`tenant_id`
  - 列表与时间范围：`created_at` / `updated_at`
- 索引数量建议控制在 **3-5 个**（不含 `PRIMARY KEY`），避免写入性能下降与维护复杂度上升。
- 避免索引失效的常见场景：
  - 避免在索引列上进行函数/表达式运算（如 `DATE(created_at)`）。
  - 避免隐式类型转换（查询参数类型应与字段类型一致）。
  - 避免以 `%` 开头的模糊匹配导致无法走索引。
  - 联合索引遵循最左前缀原则；将最常用的过滤条件放在前面。

## 2. 字段设计与类型约束

### 2.1 主键设计

- 主键可按业务选择：
  - **UUID 主键**：使用 `VARCHAR(36)` 存储 UUID，适合分布式生成、跨系统对齐与避免 ID 暴露。
  - **自增主键**：使用 `BIGINT AUTO_INCREMENT`，适合强依赖数据库自增、有序写入与高频 join 场景。
- 主键必须为 `NOT NULL`：
  - UUID 主键在 Java 侧（如 MyBatis-Plus `@TableId`）统一生成并写入。
  - 自增主键由数据库生成，业务侧不应写入该字段值。

### 2.2 状态字段与枚举值

- 使用 `VARCHAR` 存储具有明确语义的英文状态词（如 `PENDING`、`SUCCESS`），提升可读性与扩展性。
- 必须设置合理长度（如 `VARCHAR(20)`），并在业务初始化时设置 `DEFAULT`（例如 `DEFAULT 'PENDING'`）。
- 对应的 Java 枚举放在 `link-model` 模块，字段注释中列举所有枚举值。

### 2.3 数值与统计字段

- 一般统计字段建议设置为 `INT NOT NULL DEFAULT 0`，避免聚合运算时出现异常。
- 对于异步流程且"任务完成后才可计算"的指标（如 `page_count`、`chunk_count`）：
  - 可使用 `INT DEFAULT NULL`，在任务完成前保持为空，以区分"尚未计算"和"确认为 0"。

### 2.4 大文本字段

- 超长内容（如解析后原文、摘要、错误详情）统一使用 `LONGTEXT`。
- 允许 `DEFAULT NULL`，业务写入前保持为空。

## 3. 审计与时间戳字段

所有业务表建议由数据库层自动维护创建/更新时间：

- `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'`
- `updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'`

若有软删除需求，使用 `is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0 否 1 是'`，不使用 `deleted_at`。

## 4. 存储引擎与字符集

所有新表 DDL 尾部必须显式指定：

- 引擎：`ENGINE=InnoDB`（事务与锁支持）
- 字符集：`DEFAULT CHARSET=utf8mb4`（Unicode/Emoji 兼容）
- 排序规则：`COLLATE=utf8mb4_unicode_ci`

## 5. 注释规范

- 强制注释：每个字段必须带 `COMMENT`。
- 枚举字段（如 `status`、`type`）必须在注释中列举所有可能值及含义。
  - 例：`COMMENT '状态：PENDING 待处理, PROCESSING 处理中, SUCCESS 成功, FAILED 失败'`
- 表注释：建表语句末尾必须带表级别 `COMMENT`，描述表的总体业务用途。

## 6. 输出模板

当用户要求"生成建表语句/补齐字段约束/补齐索引与注释"时，按以下模板输出并据实填充：

```sql
CREATE TABLE `your_table_name` (
  -- 主键二选一：UUID 或自增（按业务选择其一）
  -- `id` varchar(36) NOT NULL COMMENT '主键 UUID',
  -- `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键自增 ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待处理, SUCCESS 成功, FAILED 失败',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 业务索引（按真实查询模式选 3-5 个）
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='表用途说明';
```

## 7. 交付后同步

DDL 落地后必须同步：

1. `scripts/db/schema.sql`：追加或更新建表语句
2. `docs/api/mysql_schema.md`：更新表说明和字段描述
3. `link-model` 模块：创建或更新对应 Entity（`@TableName`、`@TableId`、`@TableField`）
