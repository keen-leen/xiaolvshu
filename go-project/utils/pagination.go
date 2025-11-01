package utils

import (
	"strconv"

	"github.com/gin-gonic/gin"
)

const (
	// DefaultPage 默认页码
	DefaultPage = 1
	// DefaultPageSize 默认每页数量
	DefaultPageSize = 20
	// MaxPageSize 最大每页数量
	MaxPageSize = 100
)

// GetPage 获取页码参数
func GetPage(c *gin.Context) int {
	page, err := strconv.Atoi(c.DefaultQuery("page", strconv.Itoa(DefaultPage)))
	if err != nil || page < 1 {
		return DefaultPage
	}
	return page
}

// GetPageSize 获取每页数量参数
func GetPageSize(c *gin.Context) int {
	pageSize, err := strconv.Atoi(c.DefaultQuery("page_size", strconv.Itoa(DefaultPageSize)))
	if err != nil || pageSize < 1 {
		return DefaultPageSize
	}
	if pageSize > MaxPageSize {
		return MaxPageSize
	}
	return pageSize
}

// CalculateOffset 计算偏移量
func CalculateOffset(page, pageSize int) int {
	return (page - 1) * pageSize
}

// CalculateTotalPages 计算总页数
func CalculateTotalPages(total int64, pageSize int) int {
	pages := int(total) / pageSize
	if int(total)%pageSize > 0 {
		pages++
	}
	return pages
}
