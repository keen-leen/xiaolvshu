package com.xiaolvshu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolvshu.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帖子Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 按热度分推荐帖子（偏移分页）。
     * 热度公式：(like×3 + collect×2 + comment×2 + MIN(view×0.1,50)) / (小时数+2)^1.5
     * 仅返回 30 天内发布的已发布帖子。
     *
     * @param offset 分页偏移量
     * @param limit  每页数量
     * @param type   帖子类型（1-图文 2-视频），null 时不过滤
     */
    @Select("""
            SELECT p.*,
                   ROUND(
                       (p.like_count * 3 + p.collect_count * 2 + p.comment_count * 2
                           + LEAST(p.view_count * 0.1, 50))
                       / POW(GREATEST(TIMESTAMPDIFF(HOUR, p.created_at, NOW()), 0) + 2, 1.5)
                   , 6) AS hot_score
            FROM posts p
            WHERE p.is_draft = 0
              AND p.created_at >= DATE_SUB(NOW(), INTERVAL 100 DAY)
              AND (#{type} IS NULL OR p.type = #{type})
            ORDER BY hot_score DESC, p.id DESC
            LIMIT #{offset}, #{limit}
            """)
    List<Post> selectRecommended(@Param("offset") long offset,
                                 @Param("limit") int limit,
                                 @Param("type") Integer type);

    /**
     * 查询推荐帖子总数（与 selectRecommended 过滤条件保持一致）。
     *
     * @param type 帖子类型，null 时不过滤
     */
    @Select("""
            SELECT COUNT(*)
            FROM posts p
            WHERE p.is_draft = 0
              AND p.created_at >= DATE_SUB(NOW(), INTERVAL 100 DAY)
              AND (#{type} IS NULL OR p.type = #{type})
            """)
    long countRecommended(@Param("type") Integer type);
}
