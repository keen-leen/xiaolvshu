package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.Audit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核Mapper
 */
@Mapper
public interface AuditMapper extends BaseMapper<Audit> {
}
