package handlers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"xiaoshiliu/config"
	"xiaoshiliu/utils"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

const (
	// 允许的图片类型
	allowedImageTypes = ".jpg,.jpeg,.png,.gif,.webp"
	// 允许的视频类型
	allowedVideoTypes = ".mp4,.webm,.ogg"
	// 最大文件大小 (50MB)
	maxFileSize = 50 * 1024 * 1024
)

// ImageHostResponse 图床API响应结构
type ImageHostResponse struct {
	Errno    int    `json:"errno"`
	Message  string `json:"message"`
	Data struct {
		URL      string `json:"url"`
		FileName string `json:"filename"`
	} `json:"data"`
}

// 检查文件类型是否允许
func isAllowedFileType(filename string, allowedTypes string) bool {
	ext := strings.ToLower(filepath.Ext(filename))
	return strings.Contains(allowedTypes, ext)
}

// 生成唯一文件名
func generateUniqueFilename(originalFilename string) string {
	ext := filepath.Ext(originalFilename)
	return fmt.Sprintf("%s%s", uuid.New().String(), ext)
}

// 确保上传目录存在
func ensureUploadDir(dir string) error {
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("创建上传目录失败: %v", err)
	}
	return nil
}

// 保存文件到本地
func saveFileLocally(file *multipart.FileHeader, dir string) (string, error) {
	// 生成唯一文件名
	filename := generateUniqueFilename(file.Filename)
	filepath := filepath.Join(dir, filename)

	// 确保目录存在
	if err := ensureUploadDir(dir); err != nil {
		return "", err
	}

	// 保存文件
	if err := utils.SaveUploadedFile(file, filepath); err != nil {
		return "", fmt.Errorf("保存文件失败: %v", err)
	}

	// 返回文件URL
	return fmt.Sprintf("%s/%s/%s", config.AppConfig.LocalBaseURL, dir, filename), nil
}

// 上传文件到图床
func uploadToImageHost(file *multipart.FileHeader) (string, error) {
	// 创建HTTP客户端
	client := &http.Client{
		Timeout: config.AppConfig.ImageHostTimeout,
	}

	// 创建multipart请求
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// 添加文件
	part, err := writer.CreateFormFile("file", file.Filename)
	if err != nil {
		return "", fmt.Errorf("创建表单失败: %v", err)
	}

	// 打开源文件
	src, err := file.Open()
	if err != nil {
		return "", fmt.Errorf("打开文件失败: %v", err)
	}
	defer src.Close()

	// 复制文件内容
	if _, err = io.Copy(part, src); err != nil {
		return "", fmt.Errorf("复制文件失败: %v", err)
	}

	// 关闭writer
	if err = writer.Close(); err != nil {
		return "", fmt.Errorf("关闭writer失败: %v", err)
	}

	// 创建请求
	req, err := http.NewRequest("POST", config.AppConfig.ImageHostAPIURL, body)
	if err != nil {
		return "", fmt.Errorf("创建请求失败: %v", err)
	}

	// 设置请求头
	req.Header.Set("Content-Type", writer.FormDataContentType())

	// 发送请求
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("发送请求失败: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200{
		return "", fmt.Errorf("上传失败: %s", resp.Status)
	}
	// 解析响应
	var result ImageHostResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("解析响应失败: %v", err)
	}

	return result.Data.URL, nil
}

// UploadImage 上传单张图片
func UploadSingleImage(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取文件
	file, err := c.FormFile("file")
	if err != nil {
		utils.BadRequest(c, "获取文件失败")
		return
	}

	// 检查文件大小
	if file.Size > maxFileSize {
		utils.BadRequest(c, "文件大小超过限制")
		return
	}

	// 检查文件类型
	if !isAllowedFileType(file.Filename, allowedImageTypes) {
		utils.BadRequest(c, "不支持的文件类型")
		return
	}

	var imageURL string

	// 根据配置选择上传策略
	switch config.AppConfig.ImageUploadStrategy {
		case "local":
			imageURL, err = saveFileLocally(file, config.AppConfig.LocalUploadDir)
			if err != nil {
				utils.InternalServerError(c, fmt.Sprintf("上传图片失败: %v", err))
				return
			}
		case "imagehost":
			imageURL, err = uploadToImageHost(file)
			if err != nil {
				utils.InternalServerError(c, fmt.Sprintf("上传图片失败: %v", err))
				return
			}
		case "r2":
			// imageURL, err = uploadToR2(file)
			// if err != nil {
			// 	utils.InternalServerError(c, fmt.Sprintf("上传图片失败: %v", err))
			// 	return
			// }
		default:
			utils.InternalServerError(c, "无效的上传策略")
			return
	}

	if err != nil {
		utils.InternalServerError(c, fmt.Sprintf("上传失败: %v", err))
		return
	}

	utils.Success(c, gin.H{
		"originalname": file.Filename,
		"size": file.Size,
		"url": imageURL,
	})
}

// UploadVideo 上传视频
func UploadVideo(c *gin.Context) {
	user := utils.GetCurrentUser(c)
	if user == nil {
		utils.Unauthorized(c, "未登录")
		return
	}

	// 获取文件
	file, err := c.FormFile("video")
	if err != nil {
		utils.BadRequest(c, "获取文件失败")
		return
	}

	// 检查文件大小
	if file.Size > maxFileSize {
		utils.BadRequest(c, "文件大小超过限制")
		return
	}

	// 检查文件类型
	if !isAllowedFileType(file.Filename, allowedVideoTypes) {
		utils.BadRequest(c, "不支持的文件类型")
		return
	}

	// 获取视频封面
	cover, err := c.FormFile("cover")
	if err != nil {
		utils.BadRequest(c, "获取视频封面失败")
		return
	}

	// 检查封面类型
	if !isAllowedFileType(cover.Filename, allowedImageTypes) {
		utils.BadRequest(c, "不支持的封面文件类型")
		return
	}

	var videoURL, coverURL string

	// 根据配置选择上传策略
	switch config.AppConfig.VideoUploadStrategy {
	case "local":
		// 保存视频文件
		videoURL, err = saveFileLocally(file, config.AppConfig.VideoUploadDir)
		if err != nil {
			utils.InternalServerError(c, fmt.Sprintf("上传视频失败: %v", err))
			return
		}

		// 保存封面文件
		coverURL, err = saveFileLocally(cover, config.AppConfig.VideoCoverDir)
		if err != nil {
			utils.InternalServerError(c, fmt.Sprintf("上传封面失败: %v", err))
			return
		}

	case "r2":
		// TODO: 实现Cloudflare R2上传
		utils.InternalServerError(c, "R2上传策略尚未实现")
		return

	default:
		utils.InternalServerError(c, "无效的上传策略")
		return
	}

	utils.Success(c, gin.H{
		"video_url": videoURL,
		"cover_url": coverURL,
	})
}
