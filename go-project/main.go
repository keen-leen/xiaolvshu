package main

import (
	"log"
	"os"

	"xiaoshiliu/config"
	"xiaoshiliu/database"
	"xiaoshiliu/routes"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
)

// setDefaultEnv 设置默认环境变量
func setDefaultEnv() {
	// 服务器配置
	os.Setenv("PORT", "3001")
	os.Setenv("GIN_MODE", "debug")

	// 数据库配置
	os.Setenv("DB_HOST", "172.24.204.180")
	os.Setenv("DB_PORT", "3306")
	os.Setenv("DB_USER", "root")
	os.Setenv("DB_PASSWORD", "24125241")
	os.Setenv("DB_NAME", "xiaolvshu")

	// JWT配置
	os.Setenv("JWT_SECRET", "xiaoshiliu_secret_key_2025")
	os.Setenv("JWT_EXPIRES_IN", "168h")
	os.Setenv("REFRESH_TOKEN_EXPIRES_IN", "720h")

	// CORS配置
	os.Setenv("CORS_ORIGIN", "http://localhost:5173")

	// 上传配置
	os.Setenv("UPLOAD_MAX_SIZE", "50")
	os.Setenv("IMAGE_UPLOAD_STRATEGY", "imagehost")
	os.Setenv("VIDEO_UPLOAD_STRATEGY", "local")

	// 本地存储配置
	os.Setenv("LOCAL_UPLOAD_DIR", "uploads")
	os.Setenv("LOCAL_BASE_URL", "http://localhost:3001")
	os.Setenv("VIDEO_UPLOAD_DIR", "uploads/videos")
	os.Setenv("VIDEO_COVER_DIR", "uploads/covers")

	// 第三方图床配置
	os.Setenv("IMAGEHOST_API_URL", "https://api.xinyew.cn/api/jdtc")
	os.Setenv("IMAGEHOST_TIMEOUT", "60000")
}

func main() {
	// 加载环境变量
	if err := godotenv.Load(); err != nil {
		log.Printf("Error loading .env file: %v", err)
		// 如果没有找到.env文件，设置默认环境变量
		setDefaultEnv()
	}

	// 初始化配置
	config.InitConfig()

	// 初始化数据库连接
	if err := database.InitDB(); err != nil {
		log.Fatalf("Error initializing database: %v", err)
	}

	// 设置Gin模式
	if os.Getenv("GIN_MODE") == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	// 创建Gin路由
	r := gin.Default()

	// 配置CORS
	corsConfig := cors.DefaultConfig()
	corsConfig.AllowOrigins = []string{os.Getenv("CORS_ORIGIN")}
	corsConfig.AllowCredentials = true
	corsConfig.AllowHeaders = append(corsConfig.AllowHeaders, "Authorization")
	r.Use(cors.New(corsConfig))

	// 初始化路由
	routes.InitRoutes(r)

	// 启动服务器
	port := os.Getenv("PORT")
	if port == "" {
		port = "3001"
	}

	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Error starting server: %v", err)
	}
}
