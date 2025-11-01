package handlers

import (
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"
	"strings"
	"log"

	"github.com/gin-gonic/gin"
	"github.com/samber/lo"
	"gorm.io/gorm"
)

// CreateCommentRequest 创建评论请求结构
type CreateCommentRequest struct {
	PostID   int64   `json:"post_id" binding:"required"` // 笔记ID
	ParentID *int64  `json:"parent_id"`                  // 父评论ID（回复评论时使用）
	Content  string `json:"content" binding:"required"` // 评论内容
}

// UpdateCommentRequest 更新评论请求结构
type UpdateCommentRequest struct {
	Content string `json:"content" binding:"required"` // 评论内容
}

// 回复数
type ReplyCount struct {
	ParentID int64 `gorm:"column:parent_id"`
	Count    int64 `gorm:"column:count"`
}
// CreateComment 创建评论
func CreateComment(c *gin.Context) {
	var req CreateCommentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 检查笔记是否存在
	var post models.Post
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("posts").First(&post, req.PostID).Error; err != nil {
		utils.BadRequest(c, "该笔记不存在")
		return
	}

	// 如果是回复评论，检查父评论是否存在
	var parentComment models.Comment
	if req.ParentID != nil {
		if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("comments").First(&parentComment, req.ParentID).Error; err != nil {
			utils.BadRequest(c, "父评论不存在")
			return
		}
		// 检查父评论是否属于同一篇笔记
		if parentComment.PostID != req.PostID {
			utils.BadRequest(c, "父评论不属于该笔记")
			return
		}
	}

	// 开始事务
	tx := database.DB.Begin()

	// 创建评论
	comment := models.Comment{
		PostID:   req.PostID,
		UserID:   user.ID,
		ParentID: req.ParentID,
		Content:  req.Content,
	}

	if err := tx.Table("comments").Create(&comment).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建评论失败")
		return
	}

	// 更新笔记评论数
	if err := tx.Table("posts").Where("id = ?", post.ID).Update("comment_count", post.CommentCount+1).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新评论数失败")
		return
	}

	// 如果是回复评论，创建通知
	if parentComment != (models.Comment{}) {
		// 创建回复通知
		notification := models.Notification{
			UserID:    parentComment.UserID,
			SenderID:  user.ID,
			Type:      5, // 回复评论
			Title:     "有人回复了你的评论",
			TargetID:  post.ID,
			CommentID: comment.ID,
		}

		if err := tx.Table("notifications").Create(&notification).Error; err != nil {
			tx.Rollback()
			utils.InternalServerError(c, "创建通知失败")
			return
		}
	} else {
		// 创建评论通知
		notification := models.Notification{
			UserID:    post.UserID,
			SenderID:  user.ID,
			Type:      4, // 评论笔记
			Title:     "有人评论了你的笔记",
			TargetID:  post.ID,
			CommentID: comment.ID,
		}

		if err := tx.Table("notifications").Create(&notification).Error; err != nil {
			tx.Rollback()
			utils.InternalServerError(c, "创建通知失败")
			return
		}
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "保存评论失败")
		return
	}
	// TODO 处理@用户通知

	// 获取完整的评论信息
	var fullComment models.Comment
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("comments").First(&fullComment, comment.ID).Error; err != nil {
		utils.InternalServerError(c, "获取评论信息失败")
		return
	}
	fullComment.Liked = false
	fullComment.ReplyCount = 0
	fullComment.Nickname = user.Nickname
	fullComment.UserAvatar = user.Avatar
	fullComment.UserDisplayId = user.UserID
	fullComment.UserAutoId = user.ID

	utils.Success(c, fullComment)
}

// GetComment 获取评论详情
func GetComment(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "评论ID不能为空")
		return
	}

	var comment models.Comment
	if err := database.DB.Preload("User").
		Preload("Parent").
		Preload("Parent.User").
		Preload("Replies").
		Preload("Replies.User").
		First(&comment, id).Error; err != nil {
		utils.NotFound(c, "评论不存在")
		return
	}

	utils.Success(c, gin.H{"comment": comment})
}

// UpdateComment 更新评论
func UpdateComment(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "评论ID不能为空")
		return
	}

	var req UpdateCommentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 查找评论
	var comment models.Comment
	if err := database.DB.First(&comment, id).Error; err != nil {
		utils.NotFound(c, "评论不存在")
		return
	}

	// 检查权限
	if comment.UserID != user.ID {
		utils.Forbidden(c, "无权修改此评论")
		return
	}

	// 更新评论
	if err := database.DB.Model(&comment).Update("content", req.Content).Error; err != nil {
		utils.InternalServerError(c, "更新评论失败")
		return
	}

	// 获取更新后的评论信息
	var updatedComment models.Comment
	if err := database.DB.Preload("User").
		Preload("Parent").
		Preload("Parent.User").
		First(&updatedComment, comment.ID).Error; err != nil {
		utils.InternalServerError(c, "获取更新后的评论信息失败")
		return
	}

	utils.Success(c, gin.H{"comment": updatedComment})
}

// DeleteComment 删除评论
func DeleteComment(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "评论ID不能为空")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 查找评论
	var comment models.Comment
	if err := database.DB.Table("comments").First(&comment, id).Error; err != nil {
		utils.BadRequest(c, "评论不存在")
		return
	}

	// 检查权限
	if comment.UserID != user.ID {
		utils.Forbidden(c, "无权删除此评论")
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 删除相关数据
	if err := tx.Table("comments").Where("parent_id = ?", comment.ID).Delete(&models.Comment{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除回复失败")
		return
	}

	if err := tx.Table("likes").Where("target_type = ? AND target_id = ?", 2, comment.ID).Delete(&models.Like{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除点赞失败")
		return
	}

	// 删除评论
	if err := tx.Table("comments").Where("id = ?", comment.ID).Delete(&models.Comment{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除评论失败")
		return
	}

	// 更新笔记评论数
	var post models.Post
	if err := tx.Table("posts").Where("id = ?", comment.PostID).First(&post).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "获取笔记信息失败")
		return
	}

	if err := tx.Table("posts").Where("id = ?", post.ID).Update("comment_count", gorm.Expr("comment_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新评论数失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除评论失败")
		return
	}

	utils.Success(c, gin.H{"message": "删除成功"})
}

// 获取笔记评论列表
func GetComments(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "笔记ID不能为空")
		return
	}
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)
	sort := c.Query("sort")
	if sort == "" {
		sort = "desc"
	}
	// 获取顶级评论列表
	var comments []models.Comment
	query := database.DB.Table("comments").Where("post_id = ? AND parent_id IS NULL", id)
	if err := query.Order("created_at " + strings.ToUpper(sort)).Offset(pageSize * (page - 1)).Limit(pageSize).Find(&comments).Error; err != nil {
		utils.InternalServerError(c, "获取笔记评论列表失败")
		return
	}
	var total int64
	if err := query.Count(&total).Error; err != nil {
		log.Printf("获取评论总数失败: %v", err)
	}
	// 获取每个评论对应的用户信息
	userIds := lo.Map(comments, func(comment models.Comment, _ int) int64 {
		return comment.UserID
	})
	var users []models.User
	if err :=database.DB.Session(&gorm.Session{NewDB: true}).Where("id IN ?", userIds).Find(&users).Error; err != nil {
		log.Printf("获取用户信息失败: %v", err)
	}
	userMap := lo.KeyBy(users, func(user models.User) int64 {
		return user.ID
	})
	// 获取登录用户的点赞列表
	currentUser := utils.GetCurrentUser(c)
	var likeComments map[int64]bool
	if currentUser != nil {
		var likes []models.Like
		if err := database.DB.Session(&gorm.Session{NewDB: true}).Where("user_id = ? AND target_type = ?", currentUser.ID, 2).Find(&likes).Error; err != nil {
			log.Printf("获取点赞状态失败: %v", err)
		}
		likeComments = lo.Associate(likes, func(like models.Like) (int64, bool) {
			return like.TargetID, true
		})
	}
	// 获取子评论的数量
	commentIds := lo.Map(comments, func(comment models.Comment, _ int) int64 {
		return comment.ID
	})
	var replyCounts []ReplyCount
	if err := database.DB.Table("comments").Where("parent_id IN ?", commentIds).Select("parent_id, COUNT(*) as count").Group("parent_id").Find(&replyCounts).Error; err != nil {
		log.Printf("获取子评论数量失败: %v", err)
	}
	replyCountMap := lo.Associate(replyCounts, func(replyCount ReplyCount) (int64, int64) {
		return replyCount.ParentID, replyCount.Count
	})
	for i := range comments {
		user := userMap[comments[i].UserID]
		if user.ID != 0 {
			comments[i].Nickname = user.Nickname
			comments[i].UserAvatar = user.Avatar
			comments[i].UserDisplayId = user.UserID
			comments[i].UserAutoId = user.ID
		}
		if currentUser != nil {
			if _, ok := likeComments[comments[i].ID]; ok {
				comments[i].Liked = true
			}
		}
		if replyCount, ok := replyCountMap[comments[i].ID]; ok {
			comments[i].ReplyCount = replyCount
		}
	}
	utils.Success(c, gin.H{
		"comments": comments,
		"pagination": utils.Pagination{
			Page: page,
			Limit: pageSize,
			Total: total,
			Pages: int(total + 1) / pageSize,
		},
	})
}

// 获取子评论列表
func GetReplies(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "父评论ID不能为空")
		return
	}
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)
	sort := c.Query("sort")
	if sort == "" {
		sort = "desc"
	}
	var replies []models.Comment
	if err := database.DB.Table("comments").Where("parent_id = ?", id).
		Order("created_at " + strings.ToUpper(sort)).
		Offset(pageSize * (page - 1)).Limit(pageSize).
		Find(&replies).Error; err != nil {
			utils.InternalServerError(c, "获取子评论列表失败")
			return
		}
	userIds := lo.Map(replies, func(reply models.Comment, _ int) int64 {
		return reply.UserID
	})
	var users []models.User
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Where("id IN ?", userIds).Find(&users).Error; err != nil {
		log.Printf("获取用户信息失败: %v", err)
	}
	userMap := lo.KeyBy(users, func(user models.User) int64 {
		return user.ID
	})
	currentUser := utils.GetCurrentUser(c)
	var likeReplies map[int64]bool
	if currentUser != nil {
		var likes []models.Like
		if err := database.DB.Session(&gorm.Session{NewDB: true}).Where("user_id = ? AND target_type = ?", currentUser.ID, 2).Find(&likes).Error; err != nil {
			log.Printf("获取点赞状态失败: %v", err)
		}
		likeReplies = lo.Associate(likes, func(like models.Like) (int64, bool) {
			return like.TargetID, true
		})
	}
	for i := range replies {
		user := userMap[replies[i].UserID]
		if user.ID != 0 {
			replies[i].Nickname = user.Nickname
			replies[i].UserAvatar = user.Avatar
			replies[i].UserDisplayId = user.UserID
			replies[i].UserAutoId = user.ID
		}
		if currentUser != nil {
			if _, ok := likeReplies[replies[i].ID]; ok {
				replies[i].Liked = true
			}
		}
	}
	var total int64
	if err := database.DB.Table("comments").Where("parent_id = ?", id).Count(&total).Error; err != nil {
		log.Printf("获取子评论总数失败: %v", err)
	}
	utils.Success(c, gin.H{
		"comments": replies,
		"pagination": utils.Pagination{
			Page: page,
			Limit: pageSize,
			Total: total,
			Pages: int(total + 1) / pageSize,
		},
	})
}