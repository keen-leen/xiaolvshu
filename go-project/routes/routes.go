package routes

import (
	"xiaoshiliu/handlers"
	"xiaoshiliu/middleware"

	"github.com/gin-gonic/gin"
)

// InitRoutes 初始化路由
func InitRoutes(r *gin.Engine) {
	// API版本分组
	api := r.Group("/api")

	// 认证路由
	auth := api.Group("/auth")
	{
		auth.POST("/login", handlers.Login)                                       // 用户登录
		auth.POST("/register", handlers.Register)                                 // 用户注册
		auth.POST("/refresh", handlers.RefreshToken)                              // 刷新令牌
		auth.POST("/logout", middleware.AuthMiddleware(), handlers.Logout)        // 用户登出
		auth.GET("/me", middleware.AuthMiddleware(), handlers.GetCurrentUserInfo) // 获取当前用户信息
	}
	// 可选认证的路由组
	optionalAuthenticated := api.Group("/")
	optionalAuthenticated.Use(middleware.OptionalAuthMiddleware())
	{
		// 用户相关路由
		users := optionalAuthenticated.Group("/users")
		{
			users.GET("/:id", handlers.GetUserInfo)                    // 获取指定用户信息
			users.GET("/:id/stats", handlers.GetUserStats)             // 获取用户的统计信息
		}
		// 分类相关路由
		categories := api.Group("/categories")
		{
			categories.GET("", handlers.GetCategories) // 获取分类列表
		}
		// 笔记相关路由
		posts := optionalAuthenticated.Group("/posts")
		{
			posts.GET("", handlers.GetPosts)                     // 获取笔记列表
			posts.GET("/:id", handlers.GetPost)                  // 获取笔记详情
			posts.GET("/:id/comments", handlers.GetComments)     // 获取笔记评论列表
		}
		// 评论相关路由
		comments := optionalAuthenticated.Group("/comments")
		{
			comments.GET("/:id/replies", handlers.GetReplies)     // 获取子评论列表
		}
	}
	// 需要认证的路由组
	authenticated := api.Group("/")
	authenticated.Use(middleware.AuthMiddleware())
	{
		{
			authenticated.POST("/likes", handlers.Likes)   // 点赞笔记或评论
			authenticated.DELETE("/likes", handlers.Likes) // 取消点赞
		}
		
		// 用户相关路由
		users := authenticated.Group("/users")
		{
			users.PUT("/:id", handlers.UpdateUserInfo)                 // 更新指定用户信息
			users.GET("/:id/posts", handlers.GetPosts)                 // 获取用户的笔记列表
			users.GET("/:id/likes", handlers.GetUserLikes)             // 获取用户的点赞列表
			users.GET("/:id/collections", handlers.GetUserCollections) // 获取用户的收藏列表
			users.POST("/:id/follow", handlers.FollowUser)             // 关注用户
			users.DELETE("/:id/follow", handlers.FollowUser)           // 取消关注
			users.GET("/:id/follow-status", handlers.GetFollowStatus) // 获取关注状态
		}

		// 笔记相关路由
		posts := authenticated.Group("/posts")
		{
			// posts.POST("", handlers.CreatePost)                  // 创建笔记
			posts.PUT("/:id", handlers.UpdatePost)               // 更新笔记
			posts.DELETE("/:id", handlers.DeletePost)            // 删除笔记
			posts.POST("/:id/collect", handlers.CollectPost)     // 收藏笔记
			posts.DELETE("/:id/collect", handlers.UncollectPost) // 取消收藏
		}

		// 评论相关路由
		comments := authenticated.Group("/comments")
		{
			comments.POST("", handlers.CreateComment)            // 创建评论
			comments.GET("/:id", handlers.GetComment)            // 获取评论详情
			comments.PUT("/:id", handlers.UpdateComment)         // 更新评论
			comments.DELETE("/:id", handlers.DeleteComment)      // 删除评论
		}

		// 通知相关路由
		notifications := authenticated.Group("/notifications")
		{
			notifications.GET("", handlers.GetNotifications)              // 获取通知列表
			notifications.PUT("/:id/read", handlers.ReadNotification)     // 标记通知为已读
			notifications.PUT("/read-all", handlers.ReadAllNotifications) // 标记所有通知为已读
		}

		// 上传相关路由
		upload := authenticated.Group("/upload")
		{
			upload.POST("/single", handlers.UploadSingleImage) // 上传单张图片
			upload.POST("/video", handlers.UploadVideo)        // 上传视频
		}

		// 搜索相关路由
		search := authenticated.Group("/search")
		{
			search.GET("/posts", handlers.SearchPosts) // 搜索笔记
			search.GET("/users", handlers.SearchUsers) // 搜索用户
		}

		// 标签相关路由
		tags := authenticated.Group("/tags")
		{
			tags.GET("", handlers.GetTags)        // 获取标签列表
			tags.GET("/hot", handlers.GetHotTags) // 获取热门标签
		}
	}

	// 管理员路由组
	admin := api.Group("/admin")
	admin.Use(middleware.AdminAuthMiddleware())
	{
		// TODO: 实现管理员路由
	}
}
