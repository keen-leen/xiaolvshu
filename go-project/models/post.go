package models

import (
	"time"
)

type Post struct {
	ID           int64      `gorm:"primaryKey" json:"id"`
	UserID       int64      `json:"user_id"`
	Title        string    `gorm:"size:200" json:"title"`
	Content      string    `gorm:"type:text" json:"content"`
	CategoryID   *uint     `json:"category_id"`
	Type         int       `gorm:"default:1" json:"type"` // 1-image, 2-video
	IsDraft      bool      `gorm:"default:true" json:"is_draft"`
	ViewCount    uint      `gorm:"default:0" json:"view_count"`
	LikeCount    int       `gorm:"default:0" json:"like_count"`
	CollectCount int       `gorm:"default:0" json:"collect_count"`
	CommentCount int       `gorm:"default:0" json:"comment_count"`
	CreatedAt    time.Time `json:"created_at"`
	
	// Associations
	Nickname     string    `json:"nickname" gorm:"-"`
	UserAvatar   string    `json:"user_avatar" gorm:"-"`
	AuthorAccount string    `json:"author_account" gorm:"-"`
	AuthorAutoId  int64     `json:"author_auto_id" gorm:"-"`
	// AuthorAccount string    `json:"author_account"`
	// AuthorAutoId  int64     `json:"author_auto_id"`
	// Location      string    `json:"location"`
	// Verified      bool      `json:"verified"`
	Image        string    `json:"image" gorm:"-"`     // 封面
	VideoUrl     string    `json:"video_url" gorm:"-"` // 第一条视频URL
	CoverUrl     string    `json:"cover_url" gorm:"-"` // 第一条视频封面URL
	Images       []string  `json:"images" gorm:"-"`    // 图片URL列表
	Videos       []string  `json:"videos" gorm:"-"`    // 视频URL列表
	Liked        bool      `json:"liked" gorm:"-"`     // 是否已点赞
	Collected    bool      `json:"collected" gorm:"-"` // 是否已收藏
	Tags         []string  `json:"tags" gorm:"-"`      // 标签列表
}

type PostImage struct {
	ID       int64   `gorm:"primaryKey" json:"id"`
	PostID   int64   `json:"post_id"`
	ImageURL string `gorm:"size:500" json:"image_url"`
}

type PostVideo struct {
	ID       int64   `gorm:"primaryKey" json:"id"`
	PostID   int64   `json:"post_id"`
	CoverURL string `gorm:"size:500" json:"cover_url"`
	VideoURL string `gorm:"size:500" json:"video_url"`
}

type Category struct {
	ID            int64      `gorm:"primaryKey" json:"id"`
	Name          string    `gorm:"size:50;unique" json:"name"`
	CategoryTitle string    `gorm:"size:50;unique" json:"category_title"`
	CreatedAt     time.Time `json:"created_at"`
}

type Tag struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	Name      string    `gorm:"size:50;unique" json:"name"`
	UseCount  int       `gorm:"default:0" json:"use_count"`
	CreatedAt time.Time `json:"created_at"`
}

type PostTag struct {
	ID        int64      `gorm:"primaryKey" json:"id"`
	PostID    int64      `json:"post_id"`
	TagID     int64      `json:"tag_id"`
	CreatedAt time.Time `json:"created_at"`
}
