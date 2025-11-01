package models

import (
	"time"
)

type Follow struct {
	ID          int64      `gorm:"primaryKey" json:"id"`
	FollowerID  int64      `json:"follower_id"`
	FollowingID int64      `json:"following_id"`
	CreatedAt   time.Time  `json:"created_at"`

	// Associations
}

type Like struct {
	ID         int64      `gorm:"primaryKey" json:"id"`
	UserID     int64      `json:"user_id"`
	TargetType int8       `json:"target_type"` // 1-post, 2-comment
	TargetID   int64      `json:"target_id"`
	CreatedAt  time.Time  `json:"created_at"`
}

type Collection struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	UserID    int64      `json:"user_id"`
	PostID    int64      `json:"post_id"`
	CreatedAt time.Time `json:"created_at"`

	// Associations
}

type Comment struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	PostID    int64      `json:"post_id"`
	UserID    int64      `json:"user_id"`
	ParentID  *int64     `json:"parent_id"`
	Content   string    `gorm:"type:text" json:"content"`
	LikeCount int       `gorm:"default:0" json:"like_count"`
	CreatedAt time.Time `json:"created_at"`

	// Associations
	Nickname  string     `json:"nickname" gorm:"-"`
	UserAvatar string    `json:"user_avatar" gorm:"-"`
	UserAutoId  int64     `json:"user_auto_id" gorm:"-"`
	UserDisplayId string    `json:"user_display_id" gorm:"-"`
	Liked      bool      `json:"liked" gorm:"-"`
	ReplyCount int64     `json:"reply_count" gorm:"-"`
}
