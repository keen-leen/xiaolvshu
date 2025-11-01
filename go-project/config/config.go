package config

import (
	"os"
	"strconv"
	"time"
)

// Config 全局配置结构体
type Config struct {
	// 服务器配置
	Port    string
	GinMode string
	BaseURL string

	// 数据库配置
	DBHost     string
	DBPort     string
	DBUser     string
	DBPassword string
	DBName     string

	// JWT配置
	JWTSecret           string
	JWTExpiresIn        time.Duration
	RefreshTokenExpires time.Duration

	// CORS配置
	CORSOrigin string

	// 上传配置
	UploadMaxSize       int64
	ImageUploadStrategy string
	VideoUploadStrategy string

	// 本地存储配置
	LocalUploadDir string
	LocalBaseURL   string
	VideoUploadDir string
	VideoCoverDir  string

	// 第三方图床配置
	ImageHostAPIURL  string
	ImageHostTimeout time.Duration

	// Cloudflare R2配置
	R2AccessKeyID     string
	R2SecretAccessKey string
	R2Endpoint        string
	R2BucketName      string
	R2AccountID       string
	R2Region          string
	R2PublicURL       string
}

var AppConfig Config

// InitConfig 初始化配置
func InitConfig() {
	AppConfig = Config{
		// 服务器配置
		Port:    getEnvOrDefault("PORT", "3001"),
		GinMode: getEnvOrDefault("GIN_MODE", "debug"),
		BaseURL: getEnvOrDefault("API_BASE_URL", "http://localhost:3001"),

		// 数据库配置
		DBHost:     getEnvOrDefault("DB_HOST", "172.24.204.180"),
		DBPort:     getEnvOrDefault("DB_PORT", "3306"),
		DBUser:     getEnvOrDefault("DB_USER", "root"),
		DBPassword: getEnvOrDefault("DB_PASSWORD", "24125241"),
		DBName:     getEnvOrDefault("DB_NAME", "xiaolvshu"),

		// JWT配置
		JWTSecret:           getEnvOrDefault("JWT_SECRET", "xiaoshiliu_secret_key_2025"),
		JWTExpiresIn:        parseDuration(getEnvOrDefault("JWT_EXPIRES_IN", "168h")),
		RefreshTokenExpires: parseDuration(getEnvOrDefault("REFRESH_TOKEN_EXPIRES_IN", "720h")),

		// CORS配置
		CORSOrigin: getEnvOrDefault("CORS_ORIGIN", "http://localhost:5173"),

		// 上传配置
		UploadMaxSize:       parseInt64(getEnvOrDefault("UPLOAD_MAX_SIZE", "50")),
		ImageUploadStrategy: getEnvOrDefault("IMAGE_UPLOAD_STRATEGY", "imagehost"),
		VideoUploadStrategy: getEnvOrDefault("VIDEO_UPLOAD_STRATEGY", "local"),

		// 本地存储配置
		LocalUploadDir: getEnvOrDefault("LOCAL_UPLOAD_DIR", "uploads"),
		LocalBaseURL:   getEnvOrDefault("LOCAL_BASE_URL", "http://localhost:3001"),
		VideoUploadDir: getEnvOrDefault("VIDEO_UPLOAD_DIR", "uploads/videos"),
		VideoCoverDir:  getEnvOrDefault("VIDEO_COVER_DIR", "uploads/covers"),

		// 第三方图床配置
		ImageHostAPIURL:  getEnvOrDefault("IMAGEHOST_API_URL", "https://api.xinyew.cn/api/jdtc"),
		ImageHostTimeout: parseDuration(getEnvOrDefault("IMAGEHOST_TIMEOUT", "60000ms")),

		// Cloudflare R2配置
		R2AccessKeyID:     os.Getenv("R2_ACCESS_KEY_ID"),
		R2SecretAccessKey: os.Getenv("R2_SECRET_ACCESS_KEY"),
		R2Endpoint:        os.Getenv("R2_ENDPOINT"),
		R2BucketName:      os.Getenv("R2_BUCKET_NAME"),
		R2AccountID:       os.Getenv("R2_ACCOUNT_ID"),
		R2Region:          getEnvOrDefault("R2_REGION", "auto"),
		R2PublicURL:       os.Getenv("R2_PUBLIC_URL"),
	}
}

// 工具函数：获取环境变量，如果不存在则返回默认值
func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// 工具函数：解析时间duration
func parseDuration(value string) time.Duration {
	duration, err := time.ParseDuration(value)
	if err != nil {
		return 24 * time.Hour // 默认1天
	}
	return duration
}

// 工具函数：解析int64
func parseInt64(value string) int64 {
	result, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return 50 // 默认50MB
	}
	return result
}
