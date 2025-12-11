package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户会话Mapper
 */
@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
}
