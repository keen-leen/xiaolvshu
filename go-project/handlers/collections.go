package handlers

import (
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
)

// CollectPost 收藏笔记
func CollectPost(c *gin.Context) {
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

	// 检查笔记是否存在
	var post models.Post
	if err := database.DB.First(&post, id).Error; err != nil {
		utils.NotFound(c, "笔记不存在")
		return
	}

	// 检查是否已收藏
	var existingCollection models.Collection
	if err := database.DB.Where("user_id = ? AND post_id = ?",
		user.ID, post.ID).First(&existingCollection).Error; err == nil {
		utils.BadRequest(c, "已经收藏过了")
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 创建收藏记录
	collection := models.Collection{
		UserID: user.ID,
		PostID: post.ID,
	}

	if err := tx.Create(&collection).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建收藏记录失败")
		return
	}

	// 更新笔记收藏数
	if err := tx.Model(&post).Update("collect_count", post.CollectCount+1).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新收藏数失败")
		return
	}

	// 创建收藏通知
	notification := models.Notification{
		UserID:   post.UserID,
		SenderID: user.ID,
		Type:     3, // 3-收藏
		Title:    "有人收藏了你的笔记",
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
		utils.InternalServerError(c, "收藏失败")
		return
	}

	utils.Success(c, gin.H{"message": "收藏成功"})
}

// UncollectPost 取消收藏笔记
func UncollectPost(c *gin.Context) {
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

	// 检查笔记是否存在
	var post models.Post
	if err := database.DB.First(&post, id).Error; err != nil {
		utils.NotFound(c, "笔记不存在")
		return
	}

	// 检查是否已收藏
	var collection models.Collection
	if err := database.DB.Where("user_id = ? AND post_id = ?",
		user.ID, post.ID).First(&collection).Error; err != nil {
		utils.BadRequest(c, "还没有收藏")
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 删除收藏记录
	if err := tx.Delete(&collection).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除收藏记录失败")
		return
	}

	// 更新笔记收藏数
	if err := tx.Model(&post).Update("collect_count", post.CollectCount-1).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新收藏数失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "取消收藏失败")
		return
	}

	utils.Success(c, gin.H{"message": "取消收藏成功"})
}
