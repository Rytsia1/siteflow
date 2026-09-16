package com.siteflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.UserWithRole;

@Mapper
public interface UserMapper {

    @Select("SELECT u.id AS id, u.username AS username, u.password_hash AS password_hash, "
            + "r.role_name AS role_name "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE u.username = #{username}")
    UserWithRole findByUsername(String username);
}
