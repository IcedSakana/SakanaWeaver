package com.agent.persist.mapper;

import com.agent.persist.entity.SessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Session MyBatis mapper.
 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {

    @Select("SELECT * FROM t_session WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<SessionEntity> selectByUserId(@Param("userId") String userId);

    @Update("UPDATE t_session SET status = #{status} WHERE session_id = #{sessionId}")
    void updateStatus(@Param("sessionId") String sessionId, @Param("status") String status);

    @Update("UPDATE t_session SET last_active_at = #{lastActiveAt} WHERE session_id = #{sessionId}")
    void updateLastActiveAt(@Param("sessionId") String sessionId, @Param("lastActiveAt") LocalDateTime lastActiveAt);
}
