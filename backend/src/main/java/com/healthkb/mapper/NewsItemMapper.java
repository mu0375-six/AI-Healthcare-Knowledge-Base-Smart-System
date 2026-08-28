package com.healthkb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthkb.entity.NewsItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NewsItemMapper extends BaseMapper<NewsItem> {
}
