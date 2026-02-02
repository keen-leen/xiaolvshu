# Redis 模块使用指南

本项目已集成 Redis 缓存模块，提供了完善的工具类和注解支持。

## 项目结构

```
src/main/java/com/xiaolvshu/
├── common/
│   ├── annotation/
│   │   ├── RateLimit.java          # 限流注解
│   │   └── DistributedLock.java    # 分布式锁注解
│   └── constant/
│       ├── RedisKeyConstant.java   # Redis Key 常量
│       └── RedisExpireConstant.java # 过期时间常量
├── config/
│   ├── RedisConfig.java            # Redis 配置类
│   ├── RateLimitAspect.java        # 限流切面
│   └── DistributedLockAspect.java  # 分布式锁切面
├── service/
│   ├── RedisService.java           # Redis 操作服务
│   └── CacheService.java           # 业务缓存服务
└── utils/
    └── RedisKeyUtil.java           # Key 构建工具
```

## 快速开始

### 1. 注入服务

```java
@Service
@RequiredArgsConstructor
public class YourService {
    
    private final RedisService redisService;    // 底层 Redis 操作
    private final CacheService cacheService;    // 业务缓存封装
}
```

### 2. 基础缓存操作

```java
// 设置缓存（带过期时间，单位：秒）
redisService.set("key", value, 3600);

// 获取缓存
Object value = redisService.get("key");

// 获取并转换类型
User user = redisService.get("key", User.class);

// 删除缓存
redisService.delete("key");

// 判断 key 是否存在
boolean exists = redisService.hasKey("key");
```

### 3. 使用 Key 常量（推荐）

```java
// 使用 RedisKeyUtil 构建 key，保证命名规范
String key = RedisKeyUtil.getUserInfoKey(userId);
redisService.set(key, userInfo, RedisExpireConstant.USER_INFO_EXPIRE);
```

### 4. 缓存加载（防止缓存穿透）

```java
// 获取缓存，如果不存在则从数据库加载
User user = cacheService.getOrLoad(
    RedisKeyUtil.getUserInfoKey(userId),
    User.class,
    RedisExpireConstant.USER_INFO_EXPIRE,
    () -> userMapper.selectById(userId)  // 数据库回调
);

// 带分布式锁的缓存加载（防止缓存击穿）
User user = cacheService.getOrLoadWithLock(
    RedisKeyUtil.getUserInfoKey(userId),
    User.class,
    RedisExpireConstant.USER_INFO_EXPIRE,
    () -> userMapper.selectById(userId)
);
```

### 5. 计数器操作

```java
// 帖子浏览量递增
String key = RedisKeyUtil.getPostViewCountKey(postId);
long newCount = redisService.increment(key);

// 获取计数
long count = cacheService.getCount(key);
```

### 6. Set 集合操作（点赞/收藏场景）

```java
// 用户点赞帖子
String key = RedisKeyUtil.getUserPostLikesKey(userId);
cacheService.addToUserSet(key, postId);

// 取消点赞
cacheService.removeFromUserSet(key, postId);

// 检查是否点赞
boolean liked = cacheService.isInUserSet(key, postId);
```

### 7. 有序集合操作（排行榜场景）

```java
// 更新热门排行
cacheService.incrementHotRank(RedisKeyUtil.getHotPostListKey(), postId, 1);

// 获取热门 Top 10
Set<Object> hotPosts = cacheService.getHotRank(RedisKeyUtil.getHotPostListKey(), 0, 9);
```

## 注解使用

### 1. 接口限流 @RateLimit

```java
@RestController
public class PostController {

    // 每分钟最多 10 次请求（按 IP 限流）
    @RateLimit(period = 60, maxCount = 10, limitType = RateLimit.LimitType.IP)
    @PostMapping("/posts")
    public Result createPost(@RequestBody PostDTO dto) {
        // ...
    }
    
    // 每分钟最多 100 次请求（按用户 ID 限流）
    @RateLimit(period = 60, maxCount = 100, limitType = RateLimit.LimitType.USER)
    @GetMapping("/posts")
    public Result listPosts() {
        // ...
    }
    
    // 自定义限流 key 和提示信息
    @RateLimit(
        key = "sendSms",
        period = 60,
        maxCount = 1,
        limitType = RateLimit.LimitType.IP,
        message = "短信发送过于频繁，请稍后再试"
    )
    @PostMapping("/sms/send")
    public Result sendSms() {
        // ...
    }
}
```

### 2. 分布式锁 @DistributedLock

```java
@Service
public class OrderService {

    // 基础用法
    @DistributedLock(key = "createOrder")
    public void createOrder(OrderDTO dto) {
        // 业务逻辑
    }
    
    // 使用 SpEL 表达式动态生成锁 key
    @DistributedLock(key = "'order:' + #orderId", expireTime = 60)
    public void processOrder(Long orderId) {
        // 业务逻辑
    }
    
    // 自定义等待时间和提示
    @DistributedLock(
        key = "'user:bindPhone:' + #userId",
        expireTime = 30,
        waitTime = 0,  // 不等待，获取失败立即返回
        message = "操作进行中，请勿重复提交"
    )
    public void bindPhone(Long userId, String phone) {
        // 业务逻辑
    }
}
```

## 使用 Spring Cache 注解

项目已启用 `@EnableCaching`，可以使用 Spring Cache 注解：

```java
@Service
public class CategoryService {

    // 缓存结果，缓存名为 "categories"
    @Cacheable(value = "categories", key = "'list'")
    public List<Category> listCategories() {
        return categoryMapper.selectList(null);
    }
    
    // 更新时清除缓存
    @CacheEvict(value = "categories", key = "'list'")
    public void updateCategory(Category category) {
        categoryMapper.updateById(category);
    }
    
    // 更新缓存
    @CachePut(value = "categories", key = "#category.id")
    public Category saveCategory(Category category) {
        categoryMapper.insert(category);
        return category;
    }
}
```

## Key 命名规范

所有 Redis key 遵循以下命名规范：

```
项目名:模块名:业务名:标识
例如：xiaolvshu:user:info:123
```

### 已定义的 Key 前缀

| 前缀 | 用途 | 示例 |
|------|------|------|
| `xiaolvshu:user:info:` | 用户信息缓存 | `xiaolvshu:user:info:123` |
| `xiaolvshu:user:token:` | 用户 Token | `xiaolvshu:user:token:123` |
| `xiaolvshu:user:captcha:` | 验证码 | `xiaolvshu:user:captcha:abc123` |
| `xiaolvshu:post:detail:` | 帖子详情 | `xiaolvshu:post:detail:456` |
| `xiaolvshu:post:view_count:` | 帖子浏览量 | `xiaolvshu:post:view_count:456` |
| `xiaolvshu:post:hot_list` | 热门帖子 | - |
| `xiaolvshu:rate_limit:` | 接口限流 | `xiaolvshu:rate_limit:api:ip` |
| `xiaolvshu:lock:` | 分布式锁 | `xiaolvshu:lock:order:789` |

## 过期时间常量

使用 `RedisExpireConstant` 中定义的常量：

```java
RedisExpireConstant.ONE_MINUTE      // 60秒
RedisExpireConstant.FIVE_MINUTES    // 5分钟
RedisExpireConstant.THIRTY_MINUTES  // 30分钟
RedisExpireConstant.ONE_HOUR        // 1小时
RedisExpireConstant.ONE_DAY         // 1天
RedisExpireConstant.ONE_WEEK        // 1周

// 业务专用
RedisExpireConstant.USER_INFO_EXPIRE     // 用户信息：30分钟
RedisExpireConstant.POST_DETAIL_EXPIRE   // 帖子详情：15分钟
RedisExpireConstant.CAPTCHA_EXPIRE       // 验证码：5分钟
```

## 扩展指南

### 添加新的 Key 常量

1. 在 `RedisKeyConstant.java` 中添加常量：

```java
public static final String NEW_BUSINESS = PROJECT_PREFIX + "module:business:";
```

2. 在 `RedisKeyUtil.java` 中添加构建方法：

```java
public static String getNewBusinessKey(Long id) {
    return RedisKeyConstant.NEW_BUSINESS + id;
}
```

### 添加新的过期时间

在 `RedisExpireConstant.java` 中添加：

```java
public static final long NEW_BUSINESS_EXPIRE = TEN_MINUTES;
```

## 注意事项

1. **避免大 Key**：单个 value 不要超过 10KB，List/Set/Hash 元素不要超过 5000 个
2. **设置过期时间**：所有缓存都应该设置合理的过期时间
3. **缓存一致性**：更新数据库后及时清除或更新缓存
4. **Key 命名规范**：统一使用 `RedisKeyUtil` 构建 key
5. **异常处理**：Redis 不可用时应有降级方案
