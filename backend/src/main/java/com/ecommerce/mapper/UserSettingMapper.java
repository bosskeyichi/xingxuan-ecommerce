package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserSettingMapper extends BaseMapper<UserSetting> {
}
