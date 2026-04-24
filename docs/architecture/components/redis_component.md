# Redis 组件说明

## 1. 组件定位

Redis 组件是项目的缓存 framework，负责提供：

- Redis 基础配置
- 统一的 `RedisTemplate<String, Object>`
- 静态工具类 `RedisUtils`
- 双删缓存服务 `DoubleDeleteCacheService`

它解决的是“如何接入和使用 Redis 缓存”，不是“某个具体业务缓存怎么设计”。

## 2. 代码归属

- 模块：`link-components/toLink-components-redis`
- 主要包：`com.qingluo.link.components.redis`

## 3. 核心代码结构

| 代码 | 作用 |
| --- | --- |
| `RedisAutoConfiguration` | 自动装配 Redis 组件，并注册 `RedisUtils` 初始化与延迟删除线程池 |
| `RedisConfig` | RedisTemplate 配置 |
| `RedisUtils` | 常用 Redis 操作工具类 |
| `DoubleDeleteCacheService` | 提供双删缓存失效能力 |

## 4. 这个组件能做什么

### 4.1 `RedisUtils`

提供常用操作：

- `set`
- `get`
- `remove`
- `expire`
- `exists`
- `increment` / `decrement`
- `hSet` / `hGet` / `hDel` / `hExists`

适合场景：

- 简单 key-value 缓存
- 计数器
- hash 结构缓存
- 带 TTL 的快速缓存能力

### 4.2 `DoubleDeleteCacheService`

提供双删能力：

- 先同步删一次
- 延迟再删一次

适合场景：

- 先更新数据库，再失效缓存
- 需要降低脏缓存窗口

## 5. AI 什么时候应该用这个组件

当你要做的事情符合下面任一情况时，应优先考虑这个组件：

- 需要引入 Redis 缓存
- 需要对已有缓存做失效
- 需要更新数据库后删除缓存
- 需要简单计数、TTL、hash 缓存

如果需求是：

- 新建业务表
- 文件上传/下载
- 异步消息投递

那不应该先看 Redis 组件。

## 6. AI 接入时怎么操作

### 6.1 只是使用现有 Redis 能力

通常写法：

1. 在业务模块注入 `RedisTemplate<String, Object>` 或 `DoubleDeleteCacheService`
2. 在 Service 层写缓存读写逻辑
3. key 命名、TTL 和失效规则写进 `technical_design.md`
4. 若新增公共 key 规则，再同步 `middleware_contract.md`

### 6.2 想新增一个缓存读写服务

推荐做法：

1. 在 `link-service/src/main/java/com/qingluo/link/service/cache/` 下新增缓存服务接口或实现
2. 在这个缓存服务里封装 key、TTL、读写逻辑
3. 业务 Service 只依赖这个缓存服务，不直接散落写 Redis 代码

建议放置位置：

- 业务缓存封装：`link-service/.../service/cache/`
- 通用 Redis framework 代码：`link-components/toLink-components-redis/`

### 6.3 想新增双删能力

推荐做法：

1. 如果只是新增某类 key 的双删，优先在 `DoubleDeleteCacheService` 里增加一个明确方法
2. 不要在业务代码里到处手写两次 delete

## 7. AI 需要写哪些代码

### 场景一：新增业务缓存

通常需要写：

- 一个缓存 service
- key 生成方法
- put/get/evict 方法
- 业务 service 中的调用逻辑

通常不需要改：

- `RedisConfig`
- `RedisAutoConfiguration`

### 场景二：新增 Redis framework 能力

只有在下面情况才应该改 framework 模块：

- 现有 `RedisUtils` 能力不够
- 现有双删能力抽象不够
- 需要统一新的 Redis 使用模式

这类代码应放在：

- `link-components/toLink-components-redis/src/main/java/com/qingluo/link/components/redis/`

## 8. 不该怎么用

- 不要把具体业务缓存逻辑直接写进 framework 模块
- 不要在 Controller 层直接写 Redis 操作
- 不要跳过 key 规范直接随便命名
- 不要修改 DB 后不处理缓存失效

## 9. 读取顺序建议

当 AI 要使用 Redis 组件时，推荐顺序：

1. 先读本文件
2. 再读 `docs/architecture/middleware_contract.md` 中的 Redis 约定
3. 再去看相关业务代码是否已有缓存 service
4. 最后在 `technical_design.md` 中落具体方案
