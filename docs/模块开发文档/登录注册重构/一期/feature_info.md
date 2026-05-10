# 功能信息卡

## 1. 基础信息

- 模块名称：登录注册重构
- 当前期次：一期
- 业务域：user
- 当前状态：代码实现完成，测试与交付中
- 复杂度等级：L2
- 当前分支：refactor/login-register-refactor

## 2. 功能摘要

- 背景：当前注册、登录与个人资料维护中，邮箱、用户名、昵称的职责边界不够清晰，登录仍仅支持用户名，注册时昵称又与用户名存在重复感知。
- 目标：重构后端登录注册规则，明确邮箱、用户名、昵称各自职责，并将登录入口调整为“用户名或邮箱 + 密码”。
- 本期目标：
  - 明确邮箱、用户名、昵称的业务职责和可编辑边界
  - 支持邮箱或用户名登录
  - 将注册流程调整为用户名、邮箱、密码必填，昵称由后端自动生成默认值
  - 锁定个人资料中邮箱可改、昵称可改、用户名不可改的规则
- 本次不做：
  - 不改前端登录页、注册页和类型定义
  - 不做历史老用户数据迁移或补齐
  - 不修改数据库结构
  - 不扩展短信登录、邮箱验证码登录、找回密码等认证能力

## 3. 影响范围

- 关联模块：
  - `link-api`
  - `link-service`
  - `link-model`
- 关联中间件：
  - MySQL
  - Redis
- 关联外部系统：
  - 无

## 4. 文档清单

- `requirement.md`
- `technical_design.md`
- `testing_delivery.md`

## 5. 关联功能

- 历史相似功能：
  - 现有用户名密码登录
  - 现有注册与个人资料维护能力
- 后续可能扩展：
  - 登录前端文案与请求模型同步调整
  - 找回密码、邮箱校验、验证码登录等认证增强能力
- 依赖的上游功能：
  - sa-token 登录态管理
  - 用户信息缓存
- 受影响的下游功能：
  - 当前用户资料展示
  - 资料修改后的用户信息读取

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `technical_design.md`
4. `AGENTS.md`
5. `project_info.md`
6. `docs/组件和数据库约定/middleware_contract.md`

## 7. 实现完成后回填

- 已完成登录注册模块后端重构：
  - 登录请求字段从 `username` 调整为 `account`，支持用户名或邮箱登录
  - 注册请求移除 `nickname` 输入，强制邮箱必填，并由后端生成默认昵称
  - 个人资料修改新增邮箱唯一性校验
  - 登录注册模块中的用户查询已从 Service 层 `LambdaQueryWrapper` 下沉到 `SysUserMapper.xml`
- 已补充认证相关测试：
  - `AuthServiceImplTest`
  - `AuthControllerTest`
  - `UserControllerTest`
- 是否已更新 `project_info.md`：否，待测试与交付阶段统一处理
