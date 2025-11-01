package handlers

import (
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
)

// GetNotifications 获取通知列表
func GetNotifications(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var notifications []models.Notification
	var total int64

	// 查询总数
	database.DB.Model(&models.Notification{}).Where("user_id = ?", user.ID).Count(&total)

	// 查询通知列表
	if err := database.DB.Where("user_id = ?", user.ID).
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Preload("Sender").
		Find(&notifications).Error; err != nil {
		utils.InternalServerError(c, "获取通知列表失败")
		return
	}

	// 获取未读通知数量
	var unreadCount int64
	database.DB.Model(&models.Notification{}).
		Where("user_id = ? AND is_read = ?", user.ID, false).
		Count(&unreadCount)

	utils.Success(c, gin.H{
		"notifications": notifications,
		"unread_count":  unreadCount,
		"pagination": utils.Pagination{
			Page:  page,
			Limit: pageSize,
			Total: total,
			Pages: int(total) / pageSize,
		},
	})
}

// ReadNotification 标记通知为已读
func ReadNotification(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "通知ID不能为空")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 查找通知
	var notification models.Notification
	if err := database.DB.Where("id = ? AND user_id = ?", id, user.ID).
		First(&notification).Error; err != nil {
		utils.NotFound(c, "通知不存在")
		return
	}

	// 标记为已读
	if err := database.DB.Model(&notification).Update("is_read", true).Error; err != nil {
		utils.InternalServerError(c, "标记通知已读失败")
		return
	}

	utils.Success(c, gin.H{"message": "标记已读成功"})
}

// ReadAllNotifications 标记所有通知为已读
func ReadAllNotifications(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 标记所有未读通知为已读
	if err := database.DB.Model(&models.Notification{}).
		Where("user_id = ? AND is_read = ?", user.ID, false).
		Update("is_read", true).Error; err != nil {
		utils.InternalServerError(c, "标记所有通知已读失败")
		return
	}

	utils.Success(c, gin.H{"message": "标记所有通知已读成功"})
}

// DeleteNotification 删除通知
func DeleteNotification(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "通知ID不能为空")
		return
	}

	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 查找并删除通知
	if err := database.DB.Where("id = ? AND user_id = ?", id, user.ID).
		Delete(&models.Notification{}).Error; err != nil {
		utils.InternalServerError(c, "删除通知失败")
		return
	}

	utils.Success(c, gin.H{"message": "删除通知成功"})
}

// DeleteAllNotifications 删除所有通知
func DeleteAllNotifications(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 删除所有通知
	if err := database.DB.Where("user_id = ?", user.ID).
		Delete(&models.Notification{}).Error; err != nil {
		utils.InternalServerError(c, "删除所有通知失败")
		return
	}

	utils.Success(c, gin.H{"message": "删除所有通知成功"})
}

// GetUnreadCount 获取未读通知数量
func GetUnreadCount(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	var count int64
	if err := database.DB.Model(&models.Notification{}).
		Where("user_id = ? AND is_read = ?", user.ID, false).
		Count(&count).Error; err != nil {
		utils.InternalServerError(c, "获取未读通知数量失败")
		return
	}

	utils.Success(c, gin.H{"unread_count": count})
}
