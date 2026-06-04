---
name: swagger-annotation
description: SpringDoc OpenAPI 3 中文注解生成工作流。为 Spring Boot Controller 和 DTO 生成符合企业级规范的中文 Swagger 注解（@Tag、@Operation、@Parameter、@Schema）。
when_to_use: "当用户要求为 Controller 接口、RequestParam、PathVariable、RequestBody DTO 或 Response DTO 添加 Swagger/OpenAPI 注解、补充 API 文档说明时激活。触发示例：'给这个接口加swagger注解'、'补充API文档'、'添加openapi描述'、'这个接口缺文档'"
---

# SpringDoc OpenAPI 3 注解规范

## 核心原则

| 原则 | 说明 |
| --- | --- |
| **中文文档** | 所有 `name`、`description`、`summary`、`example` 必须为中文 |
| **自解释性** | 注解后的 API 文档页面对前后端协作方无需额外说明 |
| **最小侵入** | 只补缺失注解，不修改现有业务逻辑 |

## 注解包路径

项目使用 OpenAPI 3 注解（`io.swagger.v3.oas.annotations.*`），与 Knife4j UI 配合使用：

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
```

## 1. Controller 注解

### 1.1 类级别：`@Tag`

```java
@RestController
@RequestMapping("/api/v1/xxx")
@Tag(name = "XXX管理接口", description = "提供XXX的创建、查询、更新、删除功能")
public class XxxController { ... }
```

### 1.2 方法级别：`@Operation`

```java
@GetMapping("/{id}")
@SaCheckLogin
@Operation(
    summary = "查询XXX详情",
    description = "根据ID查询单条XXX记录，包含完整字段信息"
)
public Result<XxxDTO> getById(@PathVariable Long id) { ... }
```

| 参数 | 说明 | 是否必填 |
| --- | --- | --- |
| `summary` | 一句话描述接口功能，显示在 API 列表标题处 | 必填 |
| `description` | 业务语义、适用场景、注意事项 | 建议填写 |

### 1.3 参数级别：`@Parameter`

用于 `@RequestParam`、`@PathVariable`、`@RequestHeader`：

```java
@GetMapping
public Result<PageResult<XxxDTO>> list(
    @Parameter(description = "状态筛选，可选值：ACTIVE、INACTIVE", example = "ACTIVE")
    @RequestParam(required = false) String status,

    @Parameter(description = "页码，从 1 开始", example = "1")
    @RequestParam(defaultValue = "1") int page,

    @Parameter(description = "每页条数", example = "20")
    @RequestParam(defaultValue = "20") int pageSize
) { ... }
```

`@RequestBody` 参数不需要 `@Parameter`，在 DTO 的 `@Schema` 上标注即可。

## 2. DTO 注解

### 2.1 Response DTO / Request DTO

```java
@Data
@Schema(description = "XXX信息")
public class XxxDTO {

    @Schema(description = "主键ID", example = "10001")
    private Long id;

    @Schema(description = "名称", example = "我的XXX")
    private String name;

    @Schema(description = "状态：ACTIVE 正常, INACTIVE 已停用", example = "ACTIVE")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
```

| 注解位置 | 说明 |
| --- | --- |
| 类上 `@Schema(description="...")` | 描述该 DTO 的业务用途 |
| 字段上 `@Schema(description="...", example="...")` | 描述字段含义，枚举字段必须列出所有可能值 |

### 2.2 枚举/状态字段

`description` 中必须列举所有可能值及含义：

```java
@Schema(description = "任务状态：PENDING 待处理, PROCESSING 处理中, SUCCESS 成功, FAILED 失败", example = "PENDING")
private String status;
```

## 3. 典型示例（参考 UsageController）

已有完整注解的 Controller 参考 [UsageController.java](link-api/src/main/java/com/qingluo/link/api/controller/UsageController.java)，已有 DTO 注解参考 [DatasetDTO.java](link-model/src/main/java/com/qingluo/link/model/dto/response/DatasetDTO.java)。

缺少注解的 Controller 示例：[OssFileController.java](link-api/src/main/java/com/qingluo/link/api/controller/OssFileController.java)（仅有 `@RestController`，无 `@Tag` / `@Operation`）。

## 4. 执行流程

### 步骤 1：扫描目标

识别目标文件（Controller 或 DTO），列出：

- 哪些 Controller 缺 `@Tag`
- 哪些方法缺 `@Operation`
- 哪些参数缺 `@Parameter`
- 哪些 DTO 缺 `@Schema`

### 步骤 2：逐层标注

按顺序：类级 `@Tag` → 方法 `@Operation` → 参数 `@Parameter` → DTO `@Schema`

### 步骤 3：验证

```bash
# 启动服务
mvn spring-boot:run -pl link-api
# 访问 Knife4j 文档页（端口 8080）
# http://localhost:8080/doc.html
```

## 5. 禁止事项

| 禁止项 | 说明 |
| --- | --- |
| 英文 description / summary | 本项目约定全中文文档 |
| 方法无 `@Operation(summary)` | Knife4j 列表中该接口无可读标题 |
| 枚举字段不列举可能值 | 前端无法理解合法取值范围 |
| 混用 Swagger 2 注解（`io.swagger.annotations.*`） | 项目已统一使用 OpenAPI 3 注解（`io.swagger.v3.oas.annotations.*`） |
