package com.agent.persist.mapper;

import com.agent.persist.entity.AgentContextEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

/**
 * Agent context MyBatis mapper.
 */
@Mapper
public interface AgentContextMapper extends BaseMapper<AgentContextEntity> {

    @Select("SELECT * FROM t_agent_context WHERE session_id = #{sessionId}")
    AgentContextEntity selectBySessionId(@Param("sessionId") String sessionId);

    @Update("UPDATE t_agent_context SET context_data = #{contextData}, updated_at = #{updatedAt} WHERE session_id = #{sessionId}")
    void updateContextData(@Param("sessionId") String sessionId,
                           @Param("contextData") String contextData,
                           @Param("updatedAt") LocalDateTime updatedAt);

    @Delete("DELETE FROM t_agent_context WHERE session_id = #{sessionId}")
    void deleteBySessionId(@Param("sessionId") String sessionId);
}
