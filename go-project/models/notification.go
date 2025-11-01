package models

import (
	"time"
)

// Notification 通知模型
type Notification struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	UserID    int64      `json:"user_id"`                      // 接收用户ID
	SenderID  int64      `json:"sender_id"`                    // 发送用户ID
	Type      int8      `json:"type"`                         // 通知类型: 1-点赞笔记, 2-点赞评论, 3-收藏, 4-评论笔记, 5-回复评论, 6-关注, 7-评论提及, 8-笔记提及
	Title     string    `gorm:"size:200" json:"title"`        // 通知标题
	TargetID  int64     `json:"target_id" gorm:"default:null"`                    // 关联目标ID（笔记或评论ID）
	CommentID int64     `json:"comment_id" gorm:"default:null"`                   // 关联评论ID
	IsRead    bool      `gorm:"default:false" json:"is_read"` // 是否已读
	CreatedAt time.Time `json:"created_at"`                   // 通知时间
}

// UserSession 用户会话模型
type UserSession struct {
	ID           int64      `gorm:"primaryKey" json:"id"`
	UserID       int64      `json:"user_id"`
	Token        string    `gorm:"size:255;unique" json:"token"`
	RefreshToken string    `gorm:"size:255" json:"refresh_token,omitempty"`
	ExpiresAt    time.Time `json:"expires_at"`
	UserAgent    string    `gorm:"type:text" json:"user_agent,omitempty"`
	IsActive     bool      `gorm:"default:true" json:"is_active"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`

	// 关联
	User User `gorm:"foreignKey:UserID" json:"user"`
}

// Admin 管理员模型
type Admin struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	Username  string    `gorm:"size:50;unique" json:"username"`
	Password  string    `json:"-"` // 在JSON响应中隐藏密码
	CreatedAt time.Time `json:"created_at"`
}

// Audit 审核模型
type Audit struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	UserID    int64      `json:"user_id"`
	Type      int8      `json:"type"`                        // 审核类型: 1-用户审核, 2-内容审核, 3-评论审核
	Content   string    `gorm:"type:text" json:"content"`    // 审核内容
	CreatedAt time.Time `json:"created_at"`                  // 提交审核时间
	AuditTime time.Time `json:"audit_time"`                  // 完成审核时间
	Status    bool      `gorm:"default:false" json:"status"` // 审核状态: false-待审核, true-审核通过

	// 关联
	User User `gorm:"foreignKey:UserID" json:"user"`
}
