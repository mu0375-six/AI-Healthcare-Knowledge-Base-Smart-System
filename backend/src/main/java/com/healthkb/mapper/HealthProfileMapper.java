package com.healthkb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthkb.entity.HealthProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthProfileMapper extends BaseMapper<HealthProfile> {
}
