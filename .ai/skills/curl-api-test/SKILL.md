---
name: curl-api-test
description: 为 toLink-Service 的 HTTP 接口构建并执行全面的 curl 黑盒测试。分析待测接口与边界条件，必要时直连数据库或经接口造数，对本地已启动服务发起 curl 请求，断言响应，最终在对话中返回测试结果汇总。
when_to_use: "用户说『用 curl 测一下这个接口』『构建接口测试用例』『跑一遍 API 测试』『验证某个 Controller 的边界条件』『黑盒测试某接口』时使用。前提是服务已在本地启动。若用户要的是 JUnit/MockMvc 单元测试，转 auto-test；若要写验收契约，转 acceptance-generator。"
---

# Curl API Test

## 1. 定位

本 skill 负责把一个或一组 HTTP 接口，转化为可执行的 **curl 黑盒测试**，覆盖正常路径与边界条件，对本地已启动的服务真实发起请求，校验响应，并在对话中给出结构化测试结果。

它帮助 Agent 在不写 Java 测试代码的前提下，快速验证接口的真实运行行为（参数校验、认证、权限、业务分支、错误码、统一响应结构）。

它**不负责**：

- 写 JUnit / Mockito / MockMvc / SpringBootTest（转 `auto-test`）。
- 写 Gherkin 验收契约（转 `acceptance-generator`）。
- 启动 / 部署服务（默认服务已由用户启动）。
- 生成落盘的测试脚本或报告文件（本 skill 默认只在对话返回结果，不落盘）。
- 性能 / 压力 / 安全渗透测试。

## 2. 触发边界

### 2.1 适合使用

- 用户指定某个 Controller、某条接口或某组接口，要求用 curl 测试。
- 用户要求覆盖某接口的边界条件（空值、超长、非法格式、越权、未认证等）。
- 需求实现后，想用真实 HTTP 请求快速回归接口行为。
- 用户要求"造点数据再测""mock 数据测一下"。

### 2.2 不适合使用

- 服务未启动且用户不希望本 skill 代为启动 → 先提示用户启动服务。
- 用户要的是可维护的自动化测试代码 → 转 `auto-test` / `tdd`。
- 用户只要接口契约文档 → 看 `docs/api/api_contracts.md`。
- 涉及压测、并发、安全攻击向量 → 超出本 skill 范围，明确告知。

## 3. 输入前提

执行前必须确认：

1. **服务已在本地启动**，默认 base URL `http://localhost:8080`。若用户提供了其它地址，以用户为准。
2. 明确**待测接口范围**：某个 Controller / 某条接口 / 某个需求涉及的接口组。范围不清时按"步骤 1"追问。
3. 若接口需要认证，确认可用的造号方式（注册接口或直连数据库）。

不满足时按"7. 工作步骤 / 步骤 0"处理，不要盲目发请求。

## 4. 必读文件 / 上下文

按需读取，只读支撑本次测试的最小集合：

- 目标 `Controller`（`link-api/.../controller/`）：拿到路径、HTTP 方法、`@RequestMapping` 前缀、参数、是否需要认证。
- 对应请求/响应 DTO（`link-model/.../dto/`）：拿到字段、校验注解（`@NotNull`、`@Size`、`@Email` 等），用于设计边界用例。
- `link-model/.../dto/response/Result.java`：统一响应结构 `{code, message, data}`，成功 `code=200`。
- `link-core` 异常体系 / `ErrorCode`：拿到错误码与错误响应形态，用于断言失败分支。
- `docs/api/api_contracts.md`：已有接口契约，校对预期。
- 认证相关：sa-token，token 通过请求头 `satoken` 传递。

## 5. 关键项目约定（务必遵守）

- **Base URL**：`http://localhost:8080`
- **接口前缀**：各 Controller 以 `/api/v1/...` 为主，以实际 `@RequestMapping` 为准。
- **认证头**：`satoken: <token>`（不是 `Authorization: Bearer`）。token 由登录/注册接口返回的 `data.accessToken` 获取。
- **统一响应**：`{"code":200,"message":"success","data":...}`，成功判定看 `code` 字段，**不要只看 HTTP 状态码**。
- **错误响应**：业务异常返回非 200 的 `code` + `message`，需据 `ErrorCode` 断言。

## 6. 数据准备策略（已授权直连数据库任意读写）

按以下优先级造数，越靠前越优先：

1. **经接口造数**（首选）：能用注册/登录/新增类接口造出来的数据，优先走接口，保证链路真实。
2. **直连数据库读取**：需要已存在数据的 ID、状态值等，用 `mysql` 客户端查询。库名 `tolink_rag_db`，连接参数取环境变量（`DB_HOST`/`DB_USERNAME`/`DB_PASSWORD`，缺失时向用户索取）。
3. **直连数据库写入/改状态**：接口无法构造的前置状态（如特定枚举、脏数据、边界记录）可直接 `INSERT`/`UPDATE`。
   - 已获用户授权可任意读写，但仍须：
     - 优先使用**易识别的测试标记**（如用户名前缀 `cit_`、邮箱 `cit_*@test.local`），便于区分与清理。
     - 在结果汇总中**列出本次对数据库做过的写操作**，让用户可追溯。
     - 不主动删除非本次测试产生的业务数据。

> 注：本 skill 默认不落盘脚本与报告，但执行期可使用临时 shell/curl 命令；测试结束以对话汇总为最终交付。

## 7. 工作步骤

### 步骤 0：确认前提

- 确认服务可达：`curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/<已知接口>`。不可达则提示用户先启动服务（`mvn spring-boot:run -pl link-api`），不要继续。
- 确认待测范围。范围不明确时，**先追问**（见步骤 1）。

### 步骤 1：必要时追问

出现以下情况，先问最阻塞的 1 个问题再继续：

- 不清楚要测哪个接口或哪组接口。
- 接口需要认证但没有可用账号，且不确定用注册接口还是直连库造号。
- base URL 非默认且用户未提供。
- 待测接口会触发不可逆副作用（删除、外部回调、MQ 投递到生产消费者）且用户未确认可执行。

用户说"你看着测"时，可基于本 skill 约定做保守假设并在汇总中说明。

### 步骤 2：解析接口与设计用例矩阵

读取 Controller + DTO 后，为**每个待测接口**列出用例矩阵，至少覆盖：

- **正常路径**：合法参数，断言 `code=200` 与关键 `data` 字段。
- **参数校验边界**：必填缺失、空字符串、超长（突破 `@Size`）、格式非法（如非法 email）、类型错误、数值边界（0 / 负数 / 上限）。
- **认证与权限**：未带 `satoken`、token 非法/过期、越权访问他人资源。
- **业务分支**：重复创建、资源不存在、状态不允许、唯一约束冲突。
- **响应契约**：响应结构符合 `Result`，错误码符合 `ErrorCode`。

每条用例明确：方法、URL、请求头、请求体、**预期 code / 关键断言**。

### 步骤 3：准备测试数据

按"6. 数据准备策略"造数。需要认证时先登录/注册取 `accessToken`，后续用例复用该 token。

### 步骤 4：执行 curl 并采集结果

- 用 `curl -s -w "\n%{http_code}"` 同时拿响应体与 HTTP 状态码。
- 逐条执行用例，记录：HTTP 状态码、响应 `code`、`message`、关键 `data`。
- 对每条用例与"预期"比对，判定 PASS / FAIL。
- 失败用例保留原始响应，便于定位。

示例（仅示意，实际以真实接口为准）：

```bash
# 登录取 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"cit_user","password":"Test@1234"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 正常路径
curl -s -w "\n%{http_code}" http://localhost:8080/api/v1/xxx \
  -H "satoken: $TOKEN"

# 边界：未认证
curl -s -w "\n%{http_code}" http://localhost:8080/api/v1/xxx
```

### 步骤 5：清理与返回结果

- 若步骤 3 直接写入了数据库，按需清理本次测试标记数据，并在汇总中说明清理情况（已清理 / 保留原因）。
- 在对话中返回结构化测试结果（见"8. 输出内容要求"）。

## 8. 输出内容要求

最终在对话中返回（不落盘）：

1. **测试概览**：base URL、待测接口数、用例总数、PASS/FAIL 统计。
2. **用例明细表**：每行 = 接口 + 用例名 + 方法 + 预期 + 实际(code/HTTP) + 结果(PASS/FAIL)。
3. **失败详情**：每个 FAIL 的请求与原始响应、与预期的差异、可能原因。
4. **数据库写操作清单**（若有）：做过哪些 INSERT/UPDATE、是否已清理。
5. **结论与建议**：接口是否符合契约，发现的 bug 或可疑点，建议补充的用例或后续动作（如转 `auto-test` 固化为自动化测试）。

用例明细建议用 Markdown 表格，FAIL 项醒目标注。

## 9. 质量门禁

出现以下任一情况即不合格，必须修正后再交付：

- 只测了正常路径，未覆盖参数校验 / 认证 / 业务异常边界。
- 仅凭 HTTP 状态码判定结果，忽略响应体 `code` 字段。
- 用例"预期"含糊，无法判定 PASS/FAIL。
- 直连数据库做了写操作但未在汇总中说明。
- 服务不可达却仍编造测试结果。
- 把未执行的用例当作已通过上报。

## 10. 与其他 skill 的衔接

- **固化为自动化测试**：测出的关键场景转 `auto-test` / `tdd` 写成 JUnit/MockMvc。
- **接口契约不一致**：涉及契约变更转 `contract-guard` / `doc-maintenance-sync`。
- **新需求验收**：需要 Gherkin 验收契约转 `acceptance-generator`。
- **跑全量单元测试**：转 `run-all-tests`。
