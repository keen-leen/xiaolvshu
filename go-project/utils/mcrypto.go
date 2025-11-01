package utils

import (
	"crypto/sha256"
	"encoding/hex"
	"crypto/rand"
	"encoding/base64"
	"fmt"

)

// HashPassword 对密码进行哈希加密
func HashPassword(password string) (string) {
	hash := sha256.Sum256([]byte(password))
	return hex.EncodeToString(hash[:])
}

// CheckPassword 验证密码是否匹配
func CheckPassword(password, hashedPassword string) bool {
	return HashPassword(password) == hashedPassword
}

// GenerateRandomString 生成指定长度的随机字符串
func GenerateRandomString(length int) (string, error) {
	bytes := make([]byte, length)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("生成随机字符串失败: %v", err)
	}
	return base64.URLEncoding.EncodeToString(bytes)[:length], nil
}

// GenerateUserID 生成用户ID（小石榴号）
func GenerateUserID() (string, error) {
	// 生成8位随机字符串
	randomStr, err := GenerateRandomString(8)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("sl_%s", randomStr), nil
}
