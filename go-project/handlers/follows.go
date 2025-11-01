package handlers

import (
	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// FollowUser 关注用户
func FollowUser(c *gin.Context) {
	id := c.Param("id")
	if id == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}

	currentUser := utils.GetCurrentUser(c)
	if currentUser == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 检查目标用户是否存在
	var targetUser models.User
	if err := database.DB.Table("users").First(&targetUser, "user_id = ?", id).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}

	// 不能关注自己
	if targetUser.ID == currentUser.ID {
		utils.BadRequest(c, "不能关注自己")
		return
	}

	// 检查是否已关注
	var existingFollow models.Follow
	database.DB.Table("follows").Where("follower_id = ? AND following_id = ?", currentUser.ID, targetUser.ID).Find(&existingFollow)
	if existingFollow != (models.Follow{}) {
		UnfollowUser(c, targetUser.ID)
		return
	}

	// 开始事务
	tx := database.DB.Begin()

	// 创建关注记录
	follow := models.Follow{
		FollowerID:  currentUser.ID,
		FollowingID: targetUser.ID,
	}

	if err := tx.Table("follows").Create(&follow).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建关注记录失败")
		return
	}

	// 更新关注者的关注数
	if err := tx.Table("users").Where("id = ?", currentUser.ID).Update("follow_count", gorm.Expr("follow_count + ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新关注数失败")
		return
	}

	// 更新被关注者的粉丝数
	if err := tx.Table("users").Where("id = ?", targetUser.ID).Update("fans_count", gorm.Expr("fans_count + ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新粉丝数失败")
		return
	}

	// 创建关注通知
	notification := models.Notification{
		UserID:   targetUser.ID,
		SenderID: currentUser.ID,
		Type:     6, // 6-关注
		Title:    "有新粉丝关注了你",
	}

	if err := tx.Table("notifications").Create(&notification).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "创建通知失败")
		return
	}

	// 提交事务
	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "关注失败")
		return
	}

	utils.Success(c, gin.H{"message": "关注成功"})
}

// UnfollowUser 取消关注用户
func UnfollowUser(c *gin.Context, targetID int64) {
	currentUser := utils.GetCurrentUser(c)
	// 开始事务
	tx := database.DB.Begin()

	// 删除关注记录
	if err := tx.Table("follows").Where("follower_id = ? AND following_id = ?", currentUser.ID, targetID).Delete(&models.Follow{}).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "删除关注记录失败")
		return
	}

	// 更新关注者的关注数
	if err := tx.Table("users").Where("id = ?", currentUser.ID).Update("follow_count", gorm.Expr("follow_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新关注数失败")
		return
	}

	// 更新被关注者的粉丝数
	if err := tx.Table("users").Where("id = ?", targetID).Update("fans_count", gorm.Expr("fans_count - ?", 1)).Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "更新粉丝数失败")
		return
	}

	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		utils.InternalServerError(c, "取消关注失败")
		return
	}
	utils.Success(c, gin.H{"message": "取消关注成功"})
}

// GetFollowers 获取粉丝列表
func GetFollowers(c *gin.Context) {
	currentUser := utils.GetCurrentUser(c)
	if currentUser == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var follows []models.Follow
	var total int64

	// 查询总数
	database.DB.Model(&models.Follow{}).Where("following_id = ?", currentUser.ID).Count(&total)

	// 查询粉丝列表
	if err := database.DB.Where("following_id = ?", currentUser.ID).
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&follows).Error; err != nil {
		utils.InternalServerError(c, "获取粉丝列表失败")
		return
	}

	// 提取用户列表
	var users []int64
	for _, follow := range follows {
		users = append(users, follow.FollowerID)
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

// GetFollowing 获取关注列表
func GetFollowing(c *gin.Context) {
	currentUser := utils.GetCurrentUser(c)
	if currentUser == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取分页参数
	page := utils.GetPage(c)
	pageSize := utils.GetPageSize(c)

	var follows []models.Follow
	var total int64

	// 查询总数
	database.DB.Model(&models.Follow{}).Where("follower_id = ?", currentUser.ID).Count(&total)

	// 查询关注列表
	if err := database.DB.Where("follower_id = ?", currentUser.ID).
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Preload("Following").
		Find(&follows).Error; err != nil {
		utils.InternalServerError(c, "获取关注列表失败")
		return
	}

	// 提取用户列表
	var users []int64
	for _, follow := range follows {
		users = append(users, follow.FollowingID)
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

// GetFollowStatus 获取关注状态
func GetFollowStatus(c *gin.Context) {
	currentUser := utils.GetCurrentUser(c)
	if currentUser == nil {
		utils.Unauthorized(c, "未登录")
		return
	}
	targetID := c.Param("id")
	if targetID == "" {
		utils.BadRequest(c, "用户ID不能为空")
		return
	}
	var targetUser models.User
	if err := database.DB.Table("users").Where("user_id = ?", targetID).First(&targetUser).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}
	isFollowing := false
	isMutual := false
	buttonType := "follow"
	var follow models.Follow
	database.DB.Table("follows").Where("follower_id = ? AND following_id = ?", currentUser.ID, targetUser.ID).Find(&follow)
	if follow != (models.Follow{}) {
		isFollowing = true
	}
	// 检查是否互相关注
	var mutualFollow models.Follow
	database.DB.Table("follows").Where("follower_id = ? AND following_id = ?", targetUser.ID, currentUser.ID).Find(&mutualFollow)
	if follow != (models.Follow{}) && mutualFollow != (models.Follow{}) {
		isMutual = true
	}
	if currentUser.UserID == targetID {
		buttonType = "self"
	} else if isMutual {
		buttonType = "mutual"
	} else if isFollowing {
		buttonType = "unfollow"
	} else if mutualFollow != (models.Follow{}) {
		buttonType = "back"
	}
	utils.Success(c, gin.H{
		"followed": isFollowing, 
		"isFollowing": isFollowing, 
		"isMutual": isMutual, 
		"buttonType": buttonType})
}