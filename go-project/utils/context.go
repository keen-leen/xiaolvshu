package utils

import (
	"xiaoshiliu/models"

	"github.com/gin-gonic/gin"
)

// GetCurrentUser 从上下文中获取当前用户
func GetCurrentUser(c *gin.Context) *models.User {
	if user, exists := c.Get("user"); exists {
		if u, ok := user.(models.User); ok {
			return &u
		}
	}
	return nil
}

// GetCurrentAdmin 从上下文中获取当前管理员
func GetCurrentAdmin(c *gin.Context) *models.Admin {
	if admin, exists := c.Get("admin"); exists {
		if a, ok := admin.(models.Admin); ok {
			return &a
		}
	}
	return nil
}
