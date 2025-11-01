package handlers

import (
	"database/sql"
	"time"

	"xiaoshiliu/database"
	"xiaoshiliu/models"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
)

// LoginRequest 登录请求结构
type LoginRequest struct {
	UserID   string `json:"user_id" binding:"required"`
	Password string `json:"password" binding:"required"`
}

// RegisterRequest 注册请求结构
type RegisterRequest struct {
	UserID   string `json:"user_id" binding:"required,min=4,max=20"`
	Password string `json:"password" binding:"required,min=6"`
	Nickname string `json:"nickname" binding:"required,min=2,max=20"`
}

// Login 用户登录处理
func Login(c *gin.Context) {
	var req LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	// 查找用户
	var user models.User
	if err := database.DB.Where("user_id = ?", req.UserID).First(&user).Error; err != nil {
		utils.Unauthorized(c, "用户名或密码错误")
		return
	}

	// 验证密码
	if !utils.CheckPassword(req.Password, user.Password) {
		utils.Unauthorized(c, "用户名或密码错误")
		return
	}

	// 检查用户状态
	if !user.IsActive {
		utils.Forbidden(c, "账号已被禁用")
		return
	}

	// 生成令牌
	accessToken, refreshToken, err := utils.GenerateToken(&user)
	if err != nil {
		utils.InternalServerError(c, "生成令牌失败")
		return
	}

	// 创建或更新会话
	session := models.UserSession{
		UserID:       user.ID,
		Token:        accessToken,
		RefreshToken: refreshToken,
		ExpiresAt:    time.Now().Add(time.Hour * 24 * 7), // 7天
		UserAgent:    c.GetHeader("User-Agent"),
		IsActive:     true,
	}

	if err := database.DB.Create(&session).Error; err != nil {
		utils.InternalServerError(c, "创建会话失败")
		return
	}

	// 更新最后登录时间
	user.LastLoginAt = sql.NullTime{Time: time.Now(), Valid: true}
	database.DB.Save(&user)

	utils.Success(c, gin.H{
		"user":          user,
		"tokens": gin.H{
			"access_token":  accessToken,
			"refresh_token": refreshToken,
			"expires_in":    3600,
		},
	})
}

// Register 用户注册处理
func Register(c *gin.Context) {
	var req RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.ValidationError(c, "无效的请求参数")
		return
	}

	// 检查用户ID是否已存在
	var existingUser models.User
	if err := database.DB.Where("user_id = ?", req.UserID).First(&existingUser).Error; err == nil {
		utils.BadRequest(c, "用户ID已被使用")
		return
	}

	// 生成小石榴号
	userID, err := utils.GenerateUserID()
	if err != nil {
		utils.InternalServerError(c, "生成用户ID失败")
		return
	}

	// 加密密码
	hashedPassword := utils.HashPassword(req.Password)

	// 创建用户
	user := models.User{
		UserID:    userID,
		Password:  hashedPassword,
		Nickname:  req.Nickname,
		IsActive:  true,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}

	if err := database.DB.Create(&user).Error; err != nil {
		utils.InternalServerError(c, "创建用户失败")
		return
	}

	// 生成令牌
	accessToken, refreshToken, err := utils.GenerateToken(&user)
	if err != nil {
		utils.InternalServerError(c, "生成令牌失败")
		return
	}

	// 创建会话
	session := models.UserSession{
		UserID:       user.ID,
		Token:        accessToken,
		RefreshToken: refreshToken,
		ExpiresAt:    time.Now().Add(time.Hour * 24 * 7), // 7天
		UserAgent:    c.GetHeader("User-Agent"),
		IsActive:     true,
	}

	if err := database.DB.Create(&session).Error; err != nil {
		utils.InternalServerError(c, "创建会话失败")
		return
	}

	utils.Success(c, gin.H{
		"access_token":  accessToken,
		"refresh_token": refreshToken,
		"user":          user,
	})
}

// RefreshToken 刷新访问令牌
func RefreshToken(c *gin.Context) {
	refreshToken := c.GetHeader("Refresh-Token")
	if refreshToken == "" {
		utils.Unauthorized(c, "未提供刷新令牌")
		return
	}

	// 验证刷新令牌
	claims, err := utils.ParseToken(refreshToken)
	if err != nil {
		utils.Unauthorized(c, "无效的刷新令牌")
		return
	}

	// 检查会话
	var session models.UserSession
	if err := database.DB.Where("refresh_token = ? AND is_active = ?", refreshToken, true).First(&session).Error; err != nil {
		utils.Unauthorized(c, "会话已失效")
		return
	}

	// 生成新令牌
	user := models.User{ID: claims.UserID}
	newAccessToken, newRefreshToken, err := utils.GenerateToken(&user)
	if err != nil {
		utils.InternalServerError(c, "生成新令牌失败")
		return
	}

	// 更新会话
	session.Token = newAccessToken
	session.RefreshToken = newRefreshToken
	session.ExpiresAt = time.Now().Add(time.Hour * 24 * 7)
	if err := database.DB.Save(&session).Error; err != nil {
		utils.InternalServerError(c, "更新会话失败")
		return
	}

	utils.Success(c, gin.H{
		"access_token":  newAccessToken,
		"refresh_token": newRefreshToken,
	})
}

// Logout 用户登出
func Logout(c *gin.Context) {
	// 获取当前用户
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取当前会话的令牌
	token := c.GetHeader("Authorization")
	if token == "" {
		utils.Unauthorized(c, "未提供认证令牌")
		return
	}
	token = token[7:] // 移除"Bearer "前缀

	// 使会话失效
	if err := database.DB.Model(&models.UserSession{}).
		Where("user_id = ? AND token = ?", user.ID, token).
		Update("is_active", false).Error; err != nil {
		utils.InternalServerError(c, "登出失败")
		return
	}

	utils.Success(c, gin.H{"message": "登出成功"})
}

// GetCurrentUserInfo 获取当前用户信息
func GetCurrentUserInfo(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取完整的用户信息
	var fullUser models.User
	if err := database.DB.First(&fullUser, user.ID).Error; err != nil {
		utils.NotFound(c, "用户不存在")
		return
	}

	utils.Success(c, user)
}
