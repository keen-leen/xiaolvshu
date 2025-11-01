package utils

import "strconv"

// ParseInt 将字符串转换为整数
func ParseInt(s string) (int, error) {
	return strconv.Atoi(s)
}

// ParseInt64 将字符串转换为int64
func ParseInt64(s string) (int64, error) {
	return strconv.ParseInt(s, 10, 64)
}

// ParseFloat64 将字符串转换为float64
func ParseFloat64(s string) (float64, error) {
	return strconv.ParseFloat(s, 64)
}

// ParseBool 将字符串转换为布尔值
func ParseBool(s string) (bool, error) {
	return strconv.ParseBool(s)
}

// FormatInt 将整数转换为字符串
func FormatInt(i int) string {
	return strconv.Itoa(i)
}

// FormatInt64 将int64转换为字符串
func FormatInt64(i int64) string {
	return strconv.FormatInt(i, 10)
}

// FormatFloat64 将float64转换为字符串
func FormatFloat64(f float64, precision int) string {
	return strconv.FormatFloat(f, 'f', precision, 64)
}

// FormatBool 将布尔值转换为字符串
func FormatBool(b bool) string {
	return strconv.FormatBool(b)
}
