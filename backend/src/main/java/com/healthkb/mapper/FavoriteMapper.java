package com.healthkb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthkb.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
