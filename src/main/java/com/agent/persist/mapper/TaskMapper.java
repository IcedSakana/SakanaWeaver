package com.agent.persist.mapper;

import com.agent.persist.entity.TaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Task MyBatis mapper.
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

    @Select("SELECT * FROM t_task WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<TaskEntity> selectBySessionId(@Param("sessionId") String sessionId);

    @Update("UPDATE t_task SET status = #{status} WHERE task_id = #{taskId}")
    void updateStatus(@Param("taskId") String taskId, @Param("status") String status);
}
