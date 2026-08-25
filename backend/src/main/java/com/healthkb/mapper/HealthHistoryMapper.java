package com.healthkb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthkb.entity.HealthHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthHistoryMapper extends BaseMapper<HealthHistory> {
}
