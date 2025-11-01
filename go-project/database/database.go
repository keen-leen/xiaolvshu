package database

import (
	"fmt"
	"os"
	"reflect"
	"time"

	"xiaoshiliu/models"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var DB *gorm.DB

// InitDB 初始化数据库连接
func InitDB() error {
	// 构建数据库连接字符串
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		os.Getenv("DB_USER"),
		os.Getenv("DB_PASSWORD"),
		os.Getenv("DB_HOST"),
		os.Getenv("DB_PORT"),
		os.Getenv("DB_NAME"),
	)

	// 配置GORM
	config := &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
	}

	// 连接数据库
	db, err := gorm.Open(mysql.Open(dsn), config)
	if err != nil {
		return fmt.Errorf("连接数据库失败: %v", err)
	}

	// 获取底层的数据库连接
	sqlDB, err := db.DB()
	if err != nil {
		return fmt.Errorf("获取数据库实例失败: %v", err)
	}

	// 设置连接池参数
	sqlDB.SetMaxIdleConns(10)           // 最大空闲连接数
	sqlDB.SetMaxOpenConns(100)          // 最大打开连接数
	sqlDB.SetConnMaxLifetime(time.Hour) // 连接最大生命周期

	DB = db

	// 注册模型，但不进行迁移
	DB.Statement.ReflectValue = reflect.ValueOf(&models.User{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Post{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.PostImage{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.PostVideo{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Category{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Tag{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.PostTag{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Follow{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Like{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Collection{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Comment{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Notification{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.UserSession{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Admin{})
	DB.Statement.ReflectValue = reflect.ValueOf(&models.Audit{})

	return nil
}
