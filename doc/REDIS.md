# Redis 模块

## 定位

Redis 用于缓存、计数、集合、限流和分布式锁。MySQL 仍是业务事实来源，Redis 数据应允许过期、失效或重建。

主要实现：

```text
java-project/src/main/java/com/xiaolvshu/
├── common/annotation/RateLimit.java
├── common/annotation/DistributedLock.java
├── common/constant/RedisKeyConstant.java
├── common/constant/RedisExpireConstant.java
├── config/RedisConfig.java
├── config/RateLimitAspect.java
├── config/DistributedLockAspect.java
├── service/RedisService.java
└── utils/RedisKeyUtil.java
```

## 配置

后端通过以下环境变量连接 Redis：

```dotenv
REDIS_HOST=localhost
REDIS_PORT=16379
REDIS_DB=0
REDIS_TIMEOUT=5s
```

开发环境由 `docker/dev/docker-compose.yml` 启动 Redis 7，并开启 AOF `everysec` 持久化。

## RedisService

`RedisService` 封装了常用操作：

- String：设置、读取、删除、过期和存在性检查。
- 计数器：递增、递减。
- Hash：字段读写、批量写入和字段计数。
- List、Set、ZSet：队列、关系集合和排行榜类操作。
- 分布式锁所需的原子设置与安全释放。

基本用法：

```java
@Service
@RequiredArgsConstructor
public class ExampleService {
    private final RedisService redisService;

    public void cacheUser(Long userId, Object user) {
        String key = RedisKeyUtil.getUserInfoKey(userId);
        redisService.set(key, user, RedisExpireConstant.USER_INFO_EXPIRE);
    }
}
```

读取缓存时必须处理 `null`，写数据库后应删除或刷新相关缓存。

## Key 规范

统一格式：

```text
xiaolvshu:<module>:<business>:<identifier>
```

常见前缀：

| Key | 用途 |
| --- | --- |
| `xiaolvshu:user:info:{userId}` | 用户信息 |
| `xiaolvshu:user:captcha:{captchaId}` | 登录验证码 |
| `xiaolvshu:post:detail:{postId}` | 帖子详情 |
| `xiaolvshu:post:view_count:{postId}` | 浏览计数 |
| `xiaolvshu:post:user_likes:{userId}` | 用户点赞集合 |
| `xiaolvshu:notification:unread_count:{userId}` | 未读通知数 |
| `xiaolvshu:rate_limit:*` | 接口限流 |
| `xiaolvshu:lock:*` | 分布式锁 |

新增 Key 应加入 `RedisKeyConstant`，通过 `RedisKeyUtil` 构造，并在 `RedisExpireConstant` 中复用统一过期时间。除确有永久映射需求外，不应创建无过期时间的业务缓存。

## 限流

`@RateLimit` 由 `RateLimitAspect` 处理，可按 IP 或用户维度构造 Key：

```java
@RateLimit(
    period = 60,
    maxCount = 10,
    limitType = RateLimit.LimitType.USER,
    message = "操作过于频繁，请稍后再试"
)
public Result<Void> submit() {
    // ...
}
```

限流用于保护接口，不替代业务权限和幂等校验。

## 分布式锁

`@DistributedLock` 用于需要跨实例互斥的短事务：

```java
@DistributedLock(
    key = "'post:' + #postId",
    expireTime = 30,
    waitTime = 0
)
public void updatePost(Long postId) {
    // ...
}
```

锁 Key 应包含具体业务标识；锁内操作应尽量短，并设置合理超时。分布式锁不应包裹长时间外部网络调用。

## 使用原则

- 缓存命中不能绕过权限检查。
- 缓存失效优先于维护复杂的多 Key 强一致更新。
- 避免在大数据量环境使用宽泛的前缀扫描删除。
- 计数和集合若参与核心业务判断，应以数据库校验结果为准。
- 调试时不要把 Token、验证码或用户隐私写入日志。
