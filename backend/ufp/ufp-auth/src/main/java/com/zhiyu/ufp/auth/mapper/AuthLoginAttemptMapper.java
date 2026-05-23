package com.zhiyu.ufp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.ufp.auth.entity.AuthLoginAttempt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLoginAttemptMapper extends BaseMapper<AuthLoginAttempt> {}
