package handlers

import (
	"encoding/json"
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
	"github.com/samber/lo"
	"gorm.io/gorm"
)

// UpdateUserRequest 更新用户信息请求结构
type UpdateUserRequest struct {
	Nickname   *string  `json:"nickname"`    // 昵称
	Avatar     *string  `json:"avatar"`      // 头像URL
	Bio        *string  `json:"bio"`         // 个人简介
	Gender     *string  `json:"gender"`      // 性别
	ZodiacSign *string  `json:"zodiac_sign"` // 星座
	MBTI       *string  `json:"mbti"`        // MBTI人格
	Education  *string  `json:"education"`   // 学历
	Major      *string  `json:"major"`       // 专业
	Interests  []string `json:"interests"`   // 兴趣爱好
}

// UpdateUserInfo 更新用户信息
func UpdateUserInfo(c *gin.Context) {
	userID := c.Param("id")
	if userID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}

	var req UpdateUserRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	// 构建更新数据
	updates := make(map[string]interface{})
	if req.Nickname != nil {
		updates["nickname"] = *req.Nickname
	}
	if req.Avatar != nil {
		updates["avatar"] = *req.Avatar
	}
	if req.Bio != nil {
		updates["bio"] = *req.Bio
	}
	if req.Gender != nil {
		updates["gender"] = *req.Gender
	}
	if req.ZodiacSign != nil {
		updates["zodiac_sign"] = *req.ZodiacSign
	}
	if req.MBTI != nil {
		updates["mbti"] = *req.MBTI
	}
	if req.Education != nil {
		updates["education"] = *req.Education
	}
	if req.Major != nil {
		updates["major"] = *req.Major
	}
	if req.Interests != nil {
		interests, _ := json.Marshal(&req.Interests)
		updates["interests"] = string(interests)
	}

	// 更新用户信息
	if err := database.DB.Table("users").Where("user_id = ?", userID).Updates(updates).Error; err != nil {
		utils.InternalServerError(c, "更新用户信息失败")
		return
	}

	// 获取更新后的用户信息
	var updatedUser models.User
	if err := database.DB.Table("users").Where("user_id = ?", userID).First(&updatedUser).Error; err != nil {
		utils.InternalServerError(c, "获取更新后的用户信息失败")
		return
	}

	utils.Success(c, gin.H{"user": updatedUser})
}

// GetUserInfo 获取指定用户信息
func GetUserInfo(c *gin.Context) {
	userID := c.Param("id")
	if userID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}

	var user models.User
	if err := database.DB.Where("user_id = ?", userID).First(&user).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}

	utils.Success(c, user)
}

// GetUserPosts 获取用户的笔记列表
func GetUserPosts(c *gin.Context) {
	userID := c.Param("id")
	if userID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var user models.User
	if err := database.DB.Where("user_id = ?", userID).First(&user).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}

	var posts []models.Post
	var total int64

	// 查询总数
	database.DB.Model(&models.Post{}).Where("user_id = ?", user.ID).Count(&total)

	// 查询帖子列表
	if err := database.DB.Where("user_id = ?", user.ID).
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Preload("User").
		Preload("Category").
		Preload("Images").
		Preload("Videos").
		Preload("Tags").
		Find(&posts).Error; err != nil {
		utils.InternalServerError(c, "获取笔记列表失败")
		return
	}

	utils.Success(c, gin.H{
		"posts": posts,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total) / pageSize,
		},
	})
}

// GetUserLikes 获取用户的点赞列表
func GetUserLikes(c *gin.Context) {
	userID := c.Param("id")
	if userID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var user models.User
	if err := database.DB.Where("user_id = ?", userID).First(&user).Error; err != nil {
		utils.NotFound(c, "用户不存在")
		return
	}

	var likes []models.Like
	var total int64

	// 查询总数
	database.DB.Model(&models.Like{}).Where("user_id = ? AND target_type = ?", user.ID, 1).Count(&total)

	// 查询点赞列表
	if err := database.DB.Where("user_id = ? AND target_type = ?", user.ID, 1).
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&likes).Error; err != nil {
		utils.InternalServerError(c, "获取点赞列表失败")
		return
	}

	// 获取对应的笔记
	var posts []models.Post
	for _, like := range likes {
		var post models.Post
		if err := database.DB.Preload("User").
			Preload("Category").
			Preload("Images").
			Preload("Videos").
			Preload("Tags").
			First(&post, like.TargetID).Error; err == nil {
			posts = append(posts, post)
		}
	}

	utils.Success(c, gin.H{
		"posts": posts,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total) / pageSize,
		},
	})
}

// GetUserCollections 获取用户的收藏列表
func GetUserCollections(c *gin.Context) {
	userID := c.Param("id")
	if userID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var user models.User
	if err := database.DB.Where("user_id = ?", userID).First(&user).Error; err != nil {
		utils.NotFound(c, "用户不存在")
		return
	}

	// 查询总数
	var total int64
	database.DB.Table("collections").Where("user_id = ?", user.ID).Count(&total)

	// 查询收藏列表
	var collections []models.Collection
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("collections").Where("user_id = ?", user.ID).
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&collections).Error; err != nil {
		utils.InternalServerError(c, "获取收藏列表失败")
		return
	}

	// 查询对应的帖子列表
	var posts []models.Post
	postIds := lo.Map(collections, func(collection models.Collection, _ int) int64 {
		return collection.PostID
	})
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("posts").Where("id IN ?", postIds).Find(&posts).Error; err != nil {
		utils.InternalServerError(c, "获取帖子列表失败")
		return
	}
	userIds := lo.Map(posts, func(post models.Post, _ int) int64 {
		return post.UserID
	})
	var users []models.User
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("users").Where("id IN ?", userIds).Find(&users).Error; err != nil {
		utils.InternalServerError(c, "获取用户列表失败")
		return
	}
	userMap := lo.KeyBy(users, func(user models.User) int64 {
		return user.ID
	})
	for i := range posts {
		posts[i].Nickname = userMap[posts[i].UserID].Nickname
		posts[i].UserAvatar = userMap[posts[i].UserID].Avatar
	}

	utils.Success(c, gin.H{
		"posts": posts,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total) / pageSize,
		},
	})
}

// GetUserStats 获取用户的统计信息
func GetUserStats(c *gin.Context) {
	userID := c.Param("id")
	if userID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}
	// 获取用户粉丝和关注数
	var user models.User
	if err := database.DB.Table("users").Where("user_id = ?", userID).Select("id", "follow_count", "fans_count", "like_count").First(&user).Error; err != nil {
		utils.InternalServerError(c, "获取用户统计信息失败")
		return
	}
	// 获取用户发布的笔记数量
	var posts []models.Post
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("posts").Where("user_id = ? AND is_draft = 0", userID).Find(&posts).Error; err != nil {
		utils.InternalServerError(c, "获取用户发布的笔记列表失败")
		return
	}
	postCount := len(posts)
	// 获取用户发布的笔记被收藏的总数量
	postIds := lo.Map(posts, func(post models.Post, _ int) int64 {
		return post.ID
	})
	var collectCount int64
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("collections").Where("post_id IN ?", postIds).Count(&collectCount).Error; err != nil {
		utils.InternalServerError(c, "获取用户发布的笔记被收藏的总数量失败")
		return
	}
	likesAndCollects := user.LikeCount + collectCount

	utils.Success(c, gin.H{
		"follow_count": user.FollowCount,
		"fans_count": user.FansCount,
		"post_count": postCount,
		"like_count": user.LikeCount,
		"collect_count": collectCount,
		"likes_and_collects": likesAndCollects,
	})

}
