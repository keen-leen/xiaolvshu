package handlers

import (
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type LikesRequest struct {
	TargetType int `json:"target_type" binding:"required"`
	TargetID int `json:"target_id" binding:"required"`
}
// Likes 点赞笔记或评论
func Likes(c *gin.Context) {
	var req LikesRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "无效的请求参数")
		return
	}
	if req.TargetType == 1 {
		LikePost(c, req.TargetID)
		return
	} else if req.TargetType == 2 {
		LikeComment(c, req.TargetID)
		return
	}
	utils.BadRequest(c, "无效的目标类型")
}
// LikePost 点赞笔记
func LikePost(c *gin.Context, targetID int) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 检查笔记是否存在
	var post models.Post
	if err := database.DB.Table("posts").Where("id = ?", targetID).First(&post).Error; err != nil {
		utils.NotFound(c, "笔记不存在")
		return
	}

	// 检查是否已点赞
	var existingLike models.Like
	database.DB.Session(&gorm.Session{NewDB: true}).Table("likes").Where("user_id = ? AND target_type = ? AND target_id = ?", user.ID, 1, post.ID).First(&existingLike)
	if existingLike != (models.Like{}) {
		// 已经点赞，取消点赞
		UnlikePost(c, targetID)
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 创建点赞记录
	like := models.Like{
		UserID:     user.ID,
		TargetType: 1, // 1-笔记
		TargetID:   post.ID,
	}

	if err := tx.Table("likes").Create(&like).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建点赞记录失败")
		return
	}

	// 更新笔记点赞数
	// TODO:并发安全
	if err := tx.Table("posts").Where("id = ?", post.ID).Update("like_count", gorm.Expr("like_count + ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新点赞数失败")
		return
	}

	// 更新用户获赞数
	if err := tx.Table("users").Where("id = ?", post.UserID).Update("like_count", gorm.Expr("like_count + ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新用户获赞数失败")
		return
	}

	// 创建点赞通知
	notification := models.Notification{
		UserID:   post.UserID,
		SenderID: user.ID,
		Type:     1, // 1-点赞笔记
		Title:    "有人点赞了你的笔记",
		TargetID: post.ID,
	}

	if err := tx.Create(&notification).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建通知失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "点赞失败")
		return
	}

	utils.Success(c, gin.H{"liked": true})
}

// UnlikePost 取消点赞笔记
func UnlikePost(c *gin.Context, targetID int) {
	user := utils.GetCurrentUser(c)

	// 开始事务
	tx := database.DB.Begin()

	// 删除点赞记录
	if err := tx.Table("likes").Where("user_id = ? AND target_type = ? AND target_id = ?", user.ID, 1, targetID).Delete(&models.Like{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除点赞记录失败")
		return
	}

	// 更新笔记点赞数
	if err := tx.Table("posts").Where("id = ?", targetID).Update("like_count", gorm.Expr("like_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新点赞数失败")
		return
	}

	// 更新用户获赞数
	if err := tx.Table("users").Where("id = ?", user.ID).Update("like_count", gorm.Expr("like_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新用户获赞数失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "取消点赞失败")
		return
	}

	utils.Success(c, gin.H{"liked": false})
}

// LikeComment 点赞评论
func LikeComment(c *gin.Context, targetID int) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 检查评论是否存在
	var comment models.Comment
	if err := database.DB.Session(&gorm.Session{NewDB: true}).Table("comments").Where("id = ?", targetID).First(&comment).Error; err != nil {
		utils.BadRequest(c, "评论不存在")
		return
	}

	// 检查是否已点赞
	var existingLike models.Like
	database.DB.Session(&gorm.Session{NewDB: true}).Table("likes").Where("user_id = ? AND target_type = ? AND target_id = ?", user.ID, 2, comment.ID).First(&existingLike)
	if existingLike != (models.Like{}) {
		UnlikeComment(c, targetID)
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 创建点赞记录
	like := models.Like{
		UserID:     user.ID,
		TargetType: 2, // 2-评论
		TargetID:   comment.ID,
	}

	if err := tx.Table("likes").Create(&like).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建点赞记录失败")
		return
	}

	// 更新评论点赞数
	if err := tx.Table("comments").Where("id = ?", comment.ID).Update("like_count", gorm.Expr("like_count + ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新点赞数失败")
		return
	}

	// 更新用户获赞数
	if err := tx.Table("users").Where("id = ?", comment.UserID).Update("like_count", gorm.Expr("like_count + ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新用户获赞数失败")
		return
	}

	// 创建点赞通知
	notification := models.Notification{
		UserID:    comment.UserID,
		SenderID:  user.ID,
		Type:      2, // 2-点赞评论
		Title:     "有人点赞了你的评论",
		TargetID:  comment.PostID,
		CommentID: comment.ID,
	}

	if err := tx.Table("notifications").Create(&notification).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建通知失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "点赞失败")
		return
	}

	utils.Success(c, gin.H{"message": "点赞成功"})
}

// UnlikeComment 取消点赞评论
func UnlikeComment(c *gin.Context, targetID int) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 删除点赞记录
	if err := tx.Table("likes").Where("user_id = ? AND target_type = ? AND target_id = ?", user.ID, 2, targetID).Delete(&models.Like{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除点赞记录失败")
		return
	}

	// 更新评论点赞数
	if err := tx.Table("comments").Where("id = ?", targetID).Update("like_count", gorm.Expr("like_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新点赞数失败")
		return
	}

	// 更新用户获赞数
	if err := tx.Table("users").Where("id = ?", user.ID).Update("like_count", gorm.Expr("like_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新用户获赞数失败")
		return
	}
}
