package com.zhiyu.ufp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.ufp.auth.entity.AuthOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthOperationLogMapper extends BaseMapper<AuthOperationLog> { }
