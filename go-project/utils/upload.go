package utils

import (
	"fmt"
	"io"
	"mime/multipart"
	"os"
	"path/filepath"
)

// SaveUploadedFile 保存上传的文件到指定路径
func SaveUploadedFile(file *multipart.FileHeader, dst string) error {
	src, err := file.Open()
	if err != nil {
		return fmt.Errorf("打开文件失败: %v", err)
	}
	defer src.Close()

	// 确保目标目录存在
	if err := os.MkdirAll(filepath.Dir(dst), 0755); err != nil {
		return fmt.Errorf("创建目录失败: %v", err)
	}

	out, err := os.Create(dst)
	if err != nil {
		return fmt.Errorf("创建目标文件失败: %v", err)
	}
	defer out.Close()

	_, err = io.Copy(out, src)
	if err != nil {
		return fmt.Errorf("复制文件失败: %v", err)
	}

	return nil
}

// DeleteFile 删除文件
func DeleteFile(filepath string) error {
	if err := os.Remove(filepath); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("删除文件失败: %v", err)
	}
	return nil
}

// GetFileSize 获取文件大小
func GetFileSize(file *multipart.FileHeader) int64 {
	return file.Size
}

// GetFileExt 获取文件扩展名
func GetFileExt(filename string) string {
	return filepath.Ext(filename)
}

// IsImage 检查文件是否为图片
func IsImage(filename string) bool {
	ext := GetFileExt(filename)
	switch ext {
	case ".jpg", ".jpeg", ".png", ".gif", ".webp":
		return true
	default:
		return false
	}
}

// IsVideo 检查文件是否为视频
func IsVideo(filename string) bool {
	ext := GetFileExt(filename)
	switch ext {
	case ".mp4", ".webm", ".ogg":
		return true
	default:
		return false
	}
}
