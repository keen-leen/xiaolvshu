package models

import (
	"database/sql"
	"encoding/json"
	"time"

	"gorm.io/gorm"
)

type User struct {
	ID          int64         `gorm:"primaryKey" json:"id"`
	Password    string       `json:"-"` // Hide password in JSON responses
	UserID      string       `gorm:"type:varchar(50);unique" json:"user_id"`
	Nickname    string       `gorm:"size:100" json:"nickname"`
	Avatar      string       `gorm:"size:500" json:"avatar"`
	Bio         string       `gorm:"type:text" json:"bio"`
	Location    string       `gorm:"size:100" json:"location"`
	FollowCount int64          `gorm:"default:0" json:"follow_count"`
	FansCount   int64          `gorm:"default:0" json:"fans_count"`
	LikeCount   int64          `gorm:"default:0" json:"like_count"`
	IsActive    bool         `gorm:"default:true" json:"is_active"`
	LastLoginAt sql.NullTime `json:"last_login_at"`
	CreatedAt   time.Time    `json:"created_at"`
	UpdatedAt   time.Time    `json:"updated_at"`
	Gender      string       `gorm:"size:10" json:"gender"`
	ZodiacSign  string       `gorm:"size:20" json:"zodiac_sign"`
	MBTI        string       `gorm:"size:4" json:"mbti"`
	Education   string       `gorm:"size:50" json:"education"`
	Major       string       `gorm:"size:100" json:"major"`
	Interests   string       `gorm:"type:json" json:"-"` // Store as JSON string
	Verified    bool         `gorm:"default:false" json:"verified"`

	// Virtual fields for JSON
	InterestsArray []string `gorm:"-" json:"interests"`
}

// BeforeSave handles JSON encoding of Interests
func (u *User) BeforeSave(tx *gorm.DB) error {
	if len(u.InterestsArray) > 0 {
		data, err := json.Marshal(u.InterestsArray)
		if err != nil {
			return err
		}
		u.Interests = string(data)
	}
	return nil
}

// AfterFind handles JSON decoding of Interests
func (u *User) AfterFind(tx *gorm.DB) error {
	if u.Interests != "" {
		return json.Unmarshal([]byte(u.Interests), &u.InterestsArray)
	}
	return nil
}
