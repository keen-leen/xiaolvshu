package handlers

import (
	"strings"

	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
)

// SearchPosts 搜索笔记
func SearchPosts(c *gin.Context) {
	// 获取搜索参数
	keyword := strings.TrimSpace(c.Query("keyword"))
	if keyword == "" {
		utils.BadRequest(c, "搜索关键词不能为空")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	// 获取筛选参数
	categoryID := c.Query("category_id")
	tagName := c.Query("tag")

	var posts []models.Post
	var total int64

	// 构建查询
	query := database.DB.Model(&models.Post{}).Where("is_draft = ?", false)

	// 关键词搜索
	query = query.Where(
		"title LIKE ? OR content LIKE ?",
		"%"+keyword+"%",
		"%"+keyword+"%",
	)

	// 按分类筛选
	if categoryID != "" {
		query = query.Where("category_id = ?", categoryID)
	}

	// 按标签筛选
	if tagName != "" {
		query = query.Joins("JOIN post_tags ON posts.id = post_tags.post_id").
			Joins("JOIN tags ON post_tags.tag_id = tags.id").
			Where("tags.name = ?", tagName)
	}

	// 查询总数
	query.Count(&total)

	// 查询笔记列表
	if err := query.Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Preload("User").
		Preload("Category").
		Preload("Images").
		Preload("Videos").
		Preload("Tags").
		Find(&posts).Error; err != nil {
		utils.InternalServerError(c, "搜索笔记失败")
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

// SearchUsers 搜索用户
func SearchUsers(c *gin.Context) {
	// 获取搜索参数
	keyword := strings.TrimSpace(c.Query("keyword"))
	if keyword == "" {
		utils.BadRequest(c, "搜索关键词不能为空")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var users []models.User
	var total int64

	// 构建查询
	query := database.DB.Model(&models.User{}).Where("is_active = ?", true)

	// 关键词搜索
	query = query.Where(
		"user_id LIKE ? OR nickname LIKE ? OR bio LIKE ?",
		"%"+keyword+"%",
		"%"+keyword+"%",
		"%"+keyword+"%",
	)

	// 查询总数
	query.Count(&total)

	// 查询用户列表
	if err := query.Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&users).Error; err != nil {
		utils.InternalServerError(c, "搜索用户失败")
		return
	}

	utils.Success(c, gin.H{
		"users": users,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total) / pageSize,
		},
	})
}

// GetHotTags 获取热门标签
func GetHotTags(c *gin.Context) {
	limit := 20 // 默认返回20个热门标签
	if limitStr := c.Query("limit"); limitStr != "" {
		if n, err := utils.ParseInt(limitStr); err == nil && n > 0 {
			limit = n
		}
	}

	var tags []models.Tag
	if err := database.DB.Order("use_count DESC").
		Limit(limit).
		Find(&tags).Error; err != nil {
		utils.InternalServerError(c, "获取热门标签失败")
		return
	}

	utils.Success(c, gin.H{"tags": tags})
}

// GetCategories 获取分类列表
func GetCategories(c *gin.Context) {
	var categories []models.Category
	if err := database.DB.Order("id ASC").Find(&categories).Error; err != nil {
		utils.InternalServerError(c, "获取分类列表失败")
		return
	}

	utils.Success(c, categories)
}

// GetTags 获取标签列表
func GetTags(c *gin.Context) {
	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var tags []models.Tag
	var total int64

	// 查询总数
	database.DB.Model(&models.Tag{}).Count(&total)

	// 查询标签列表
	if err := database.DB.Order("use_count DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&tags).Error; err != nil {
		utils.InternalServerError(c, "获取标签列表失败")
		return
	}

	utils.Success(c, gin.H{
		"tags": tags,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total) / pageSize,
		},
	})
}
