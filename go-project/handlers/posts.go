package handlers

import (
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"log"

	"github.com/gin-gonic/gin"
	"github.com/samber/lo"
	"gorm.io/gorm"
)

// CreatePostRequest 创建笔记请求结构
type CreatePostRequest struct {
	Title      string   `json:"title" binding:"required"`            // 标题
	Content    string   `json:"content" binding:"required"`          // 内容
	CategoryID *uint    `json:"category_id"`                         // 分类ID
	Type       int      `json:"type" binding:"required"`             // 类型：1-图片笔记，2-视频笔记
	IsDraft    bool     `json:"is_draft"`                            // 是否为草稿
	Images     []string `json:"images" binding:"required_if=Type 1"` // 图片URL列表
	Videos     []struct {
		VideoURL string `json:"video_url"` // 视频URL
		CoverURL string `json:"cover_url"` // 封面URL
	} `json:"videos" binding:"required_if=Type 2"` // 视频信息
	Tags []string `json:"tags"` // 标签列表
}

// UpdatePostRequest 更新笔记请求结构
type UpdatePostRequest struct {
	Title      *string  `json:"title"`       // 标题
	Content    *string  `json:"content"`     // 内容
	CategoryID *uint    `json:"category_id"` // 分类ID
	IsDraft    *bool    `json:"is_draft"`    // 是否为草稿
	Images     []string `json:"images"`      // 图片URL列表
	Videos     []struct {
		VideoURL string `json:"video_url"` // 视频URL
		CoverURL string `json:"cover_url"` // 封面URL
	} `json:"videos"` // 视频信息
	Tags []string `json:"tags"` // 标签列表
}

// CreatePost 创建笔记
func CreatePost(c *gin.Context) {
	var req CreatePostRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 开始数据库事务
	tx := database.DB.Begin()

	// 创建笔记
	post := models.Post{
		UserID:     user.ID,
		Title:      req.Title,
		Content:    req.Content,
		CategoryID: req.CategoryID,
		Type:       req.Type,
		IsDraft:    req.IsDraft,
	}

	if err := tx.Create(&post).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建笔记失败")
		return
	}

	// 处理图片
	if req.Type == 1 && len(req.Images) > 0 {
		for _, imageURL := range req.Images {
			image := models.PostImage{
				PostID:   post.ID,
				ImageURL: imageURL,
			}
			if err := tx.Create(&image).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "保存图片信息失败")
				return
			}
		}
	}

	// 处理视频
	if req.Type == 2 && len(req.Videos) > 0 {
		for _, video := range req.Videos {
			postVideo := models.PostVideo{
				PostID:   post.ID,
				VideoURL: video.VideoURL,
				CoverURL: video.CoverURL,
			}
			if err := tx.Create(&postVideo).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "保存视频信息失败")
				return
			}
		}
	}

	// 处理标签
	if len(req.Tags) > 0 {
		for _, tagName := range req.Tags {
			var tag models.Tag
			// 查找或创建标签
			if err := tx.Where("name = ?", tagName).FirstOrCreate(&tag, models.Tag{
				Name: tagName,
			}).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "处理标签失败")
				return
			}

			// 创建笔记-标签关联
			postTag := models.PostTag{
				PostID: post.ID,
				TagID:  tag.ID,
			}
			if err := tx.Create(&postTag).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "创建标签关联失败")
				return
			}

			// 更新标签使用次数
			if err := tx.Model(&tag).Update("use_count", tag.UseCount+1).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "更新标签使用次数失败")
				return
			}
		}
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "保存笔记失败")
		return
	}

	// 获取完整的笔记信息
	var fullPost models.Post
	if err := database.DB.Preload("User").
		Preload("Category").
		Preload("Images").
		Preload("Videos").
		Preload("Tags").
		First(&fullPost, post.ID).Error; err != nil {
		utils.InternalServerError(c, "获取笔记信息失败")
		return
	}

	utils.Success(c, gin.H{"post": fullPost})
}

// GetPosts 获取笔记列表
func GetPosts(c *gin.Context) {
	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	// 获取筛选参数
	category := c.Query("category")
	userID := c.Param("id")

	var posts []models.Post
	var total int64

	// 创建一个基础查询
	query := database.DB.Table("posts").Where("is_draft = ?", false)

	// 添加筛选条件
	if category != "" {
		query = query.Where("category_id = ?", category)
	}
	if userID != "" {
		query = query.Where("user_id = ?", userID)
	}

	// 查询笔记列表
	if err := query.Order("created_at DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&posts).Error; err != nil {
		log.Println("获取笔记列表失败:", err)
		utils.InternalServerError(c, "获取笔记列表失败")
		return
	}

	// 查询总数
	if err := query.Count(&total).Error; err != nil {
		log.Printf("统计笔记总数失败: %v", err)
		utils.InternalServerError(c, "获取笔记列表失败")
		return
	}

	post_ids := lo.Map(posts, func(post models.Post, _ int) int64 {
		return int64(post.ID)
	})
	// 获取帖子对应的所有图片资源并做映射
	var images []models.PostImage
	if err := database.DB.Table("post_images").Where("post_id IN ?", post_ids).Select("post_id, image_url").Find(&images).Error; err != nil {
		log.Printf("获取图片资源失败: %v", err)
		utils.InternalServerError(c, "获取图片资源失败")
		return
	}
	image_map := lo.GroupBy(images, func(image models.PostImage) int64 {
		return image.PostID
	})
	// 获取帖子对应的所有视频资源并做映射
	var videos []models.PostVideo
	if err := database.DB.Table("post_videos").Where("post_id IN ?", post_ids).Select("post_id, video_url, cover_url").Find(&videos).Error; err != nil {
		log.Printf("获取视频资源失败: %v", err)
		utils.InternalServerError(c, "获取视频资源失败")
		return
	}
	video_map := lo.GroupBy(videos, func(video models.PostVideo) int64 {
		return video.PostID
	})
	// 获取所有帖子的作者id
	user_ids := lo.Map(posts, func(post models.Post, _ int) int64 {
		return post.UserID
	})
	var users []models.User
	if err := database.DB.Table("users").Where("id IN ?", user_ids).Select("id, user_id, nickname, avatar").Find(&users).Error; err != nil {
		log.Printf("获取用户资源失败: %v", err)
		utils.InternalServerError(c, "获取用户资源失败")
		return
	}	
	user_map := lo.KeyBy(users, func(user models.User) int64 {
		return user.ID
	})
	// 获取当前用户的点赞列表
	currentUser := utils.GetCurrentUser(c)
	var likes []models.Like
	if currentUser != nil {
		if err := database.DB.Table("likes").Where("user_id = ? AND target_type = ?", currentUser.ID, 1).Find(&likes).Error; err != nil {
			utils.InternalServerError(c, "获取当前用户的点赞列表失败")
			return
		}
	}
	like_posts := lo.KeyBy(likes, func(like models.Like) int64 {
		return like.TargetID
	})

	// 获取每个笔记的图片、标签和用户点赞收藏状态
	for i := range posts {
		if posts[i].Type == 2 {
			// 如果是视频笔记
			videos := video_map[posts[i].ID]
			if len(videos) > 0 {
				posts[i].VideoUrl = videos[0].VideoURL
				posts[i].Image = videos[0].CoverURL
			}
		} else if posts[i].Type == 1 {
			images := image_map[posts[i].ID]
			if len(images) > 0 {
				imageUrls := lo.Map(images, func(image models.PostImage, _ int) string {
					return image.ImageURL
				})
				posts[i].Images = imageUrls
				posts[i].Image = imageUrls[0]
			}
		}
		if currentUser != nil {
			if _, ok := like_posts[posts[i].ID]; ok {
				posts[i].Liked = true
			}
		}
		user := user_map[posts[i].UserID]
		posts[i].Nickname = user.Nickname
		posts[i].UserAvatar = user.Avatar
		posts[i].AuthorAccount = user.UserID
		posts[i].AuthorAutoId = user.ID
	}

	utils.Success(c, gin.H{
		"posts": posts,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total + 1) / pageSize,
		},
	})
}

// GetPost 获取笔记详情
func GetPost(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "笔记ID不能为空")
		return
	}

	var post models.Post
	if err := database.DB.Table("posts").Where("id = ?", id).First(&post).Error; err != nil {
		utils.BadRequest(c, "笔记不存在")
		return
	}
	// 获取笔记的图片、视频以及点赞收藏等信息
	if post.Type == 2 {
		var videos []models.PostVideo
		if err := database.DB.Table("post_videos").Where("post_id = ?", id).Find(&videos).Error; err != nil {
			log.Printf("获取视频资源失败: %v", err)
		}
		if len(videos) > 0 {
			post.Videos = lo.Map(videos, func(video models.PostVideo, _ int) string {
				return video.VideoURL
			})
			post.VideoUrl = videos[0].VideoURL
			post.CoverUrl = videos[0].CoverURL
		}
	} else if post.Type == 1 {
		var images []models.PostImage
		if err := database.DB.Table("post_images").Where("post_id = ?", id).Find(&images).Error; err != nil {
			log.Printf("获取图片资源失败: %v", err)
		}
		if len(images) > 0 {
			post.Images = lo.Map(images, func(image models.PostImage, _ int) string {
				return image.ImageURL
			})
		}
	}
	// 获取笔记的标签
	var tags []models.Tag
	if err := database.DB.Table("post_tags pt").Joins("JOIN tags t ON pt.tag_id = t.id").Select("t.id as id, t.name as name").Where("pt.post_id = ?", id).Find(&tags).Error; err != nil {
		log.Printf("获取标签资源失败: %v", err)
	}
	post.Tags = lo.Map(tags, func(tag models.Tag, _ int) string {
		return tag.Name
	})
	// 获取笔记作者信息
	var user models.User
	if err := database.DB.Table("users").Where("id = ?", post.UserID).First(&user).Error; err != nil {
		log.Printf("获取笔记作者信息失败: %v", err)
	}
	post.Nickname = user.Nickname
	post.UserAvatar = user.Avatar
	post.AuthorAccount = user.UserID
	post.AuthorAutoId = user.ID
	// 登陆状态时检查是否点赞和收藏
	currentUser := utils.GetCurrentUser(c)
	if currentUser != nil {
		var count int64
		database.DB.Table("likes").Where("user_id = ? AND target_id = ? AND target_type = ?", currentUser.ID, id, 1).Count(&count)
		post.Liked = count > 0
		database.DB.Table("collections").Where("user_id = ? AND post_id = ?", currentUser.ID, id).Count(&count)
		post.Collected = count > 0
	}

	// 增加浏览量
	database.DB.Table("posts").Where("id = ?", id).Update("view_count", gorm.Expr("view_count + ?", 1))

	utils.Success(c, post)
}

// UpdatePost 更新笔记
func UpdatePost(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "笔记ID不能为空")
		return
	}

	var req UpdatePostRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 查找笔记
	var post models.Post
	if err := database.DB.First(&post, id).Error; err != nil {
		utils.NotFound(c, "笔记不存在")
		return
	}

	// 检查权限
	if post.UserID != user.ID {
		utils.Forbidden(c, "无权修改此笔记")
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 更新基本信息
	updates := make(map[string]interface{})
	if req.Title != nil {
		updates["title"] = *req.Title
	}
	if req.Content != nil {
		updates["content"] = *req.Content
	}
	if req.CategoryID != nil {
		updates["category_id"] = *req.CategoryID
	}
	if req.IsDraft != nil {
		updates["is_draft"] = *req.IsDraft
	}

	if err := tx.Model(&post).Updates(updates).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新笔记失败")
		return
	}

	// 更新图片
	if len(req.Images) > 0 {
		// 删除旧图片
		if err := tx.Where("post_id = ?", post.ID).Delete(&models.PostImage{}).Error; err != nil {
			tx.Rollback()
			utils.InternalServerError(c, "删除旧图片失败")
			return
		}

		// 添加新图片
		for _, imageURL := range req.Images {
			image := models.PostImage{
				PostID:   post.ID,
				ImageURL: imageURL,
			}
			if err := tx.Create(&image).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "保存图片信息失败")
				return
			}
		}
	}

	// 更新视频
	if len(req.Videos) > 0 {
		// 删除旧视频
		if err := tx.Where("post_id = ?", post.ID).Delete(&models.PostVideo{}).Error; err != nil {
			tx.Rollback()
			utils.InternalServerError(c, "删除旧视频失败")
			return
		}

		// 添加新视频
		for _, video := range req.Videos {
			postVideo := models.PostVideo{
				PostID:   post.ID,
				VideoURL: video.VideoURL,
				CoverURL: video.CoverURL,
			}
			if err := tx.Create(&postVideo).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "保存视频信息失败")
				return
			}
		}
	}

	// 更新标签
	if len(req.Tags) > 0 {
		// 删除旧标签关联
		if err := tx.Where("post_id = ?", post.ID).Delete(&models.PostTag{}).Error; err != nil {
			tx.Rollback()
			utils.InternalServerError(c, "删除旧标签关联失败")
			return
		}

		// 添加新标签
		for _, tagName := range req.Tags {
			var tag models.Tag
			// 查找或创建标签
			if err := tx.Where("name = ?", tagName).FirstOrCreate(&tag, models.Tag{
				Name: tagName,
			}).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "处理标签失败")
				return
			}

			// 创建笔记-标签关联
			postTag := models.PostTag{
				PostID: post.ID,
				TagID:  tag.ID,
			}
			if err := tx.Create(&postTag).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "创建标签关联失败")
				return
			}

			// 更新标签使用次数
			if err := tx.Model(&tag).Update("use_count", tag.UseCount+1).Error; err != nil {
				tx.Rollback()
				utils.InternalServerError(c, "更新标签使用次数失败")
				return
			}
		}
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "保存笔记失败")
		return
	}

	// 获取更新后的完整笔记信息
	var updatedPost models.Post
	if err := database.DB.Preload("User").
		Preload("Category").
		Preload("Images").
		Preload("Videos").
		Preload("Tags").
		First(&updatedPost, post.ID).Error; err != nil {
		utils.InternalServerError(c, "获取更新后的笔记信息失败")
		return
	}

	utils.Success(c, gin.H{"post": updatedPost})
}

// DeletePost 删除笔记
func DeletePost(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "笔记ID不能为空")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 查找笔记
	var post models.Post
	if err := database.DB.First(&post, id).Error; err != nil {
		utils.NotFound(c, "笔记不存在")
		return
	}

	// 检查权限
	if post.UserID != user.ID {
		utils.Forbidden(c, "无权删除此笔记")
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 删除相关数据
	if err := tx.Where("post_id = ?", post.ID).Delete(&models.PostImage{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除图片失败")
		return
	}

	if err := tx.Where("post_id = ?", post.ID).Delete(&models.PostVideo{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除视频失败")
		return
	}

	if err := tx.Where("post_id = ?", post.ID).Delete(&models.PostTag{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除标签关联失败")
		return
	}

	if err := tx.Where("post_id = ?", post.ID).Delete(&models.Comment{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除评论失败")
		return
	}

	if err := tx.Where("target_type = ? AND target_id = ?", 1, post.ID).Delete(&models.Like{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除点赞失败")
		return
	}

	if err := tx.Where("post_id = ?", post.ID).Delete(&models.Collection{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除收藏失败")
		return
	}

	// 删除笔记
	if err := tx.Delete(&post).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除笔记失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除笔记失败")
		return
	}

	utils.Success(c, gin.H{"message": "删除成功"})
}
