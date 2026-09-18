package com.siteflow.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.User;
import com.siteflow.domain.UserWithRole;

@Mapper
public interface UserMapper {

    @Select("SELECT u.id AS id, u.username AS username, u.password_hash AS password_hash, "
            + "r.role_name AS role_name, u.is_active AS is_active "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE u.username = #{username}")
    UserWithRole findByUsername(String username);

    @Select("SELECT u.id AS id, u.username AS username, u.password_hash AS password_hash, "
            + "r.role_name AS role_name, u.is_active AS is_active "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE u.id = #{id}")
    UserWithRole findUserWithRoleById(Long id);

    @Select("SELECT id, role_id, username, password_hash, full_name, job_position, "
            + "is_active, deactivated_at, created_at, updated_at "
            + "FROM users WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.role_name = 'ADMIN' AND u.is_active = TRUE")
    int countActiveAdmins();

    @Update("UPDATE users SET is_active = FALSE, full_name = #{anonymizedName}, job_position = NULL, "
            + "password_hash = #{scrambledHash}, deactivated_at = #{deactivatedAt}, updated_at = #{deactivatedAt} "
            + "WHERE id = #{id}")
    int deactivateAndAnonymize(
            @Param("id") Long id,
            @Param("anonymizedName") String anonymizedName,
            @Param("scrambledHash") String scrambledHash,
            @Param("deactivatedAt") LocalDateTime deactivatedAt);
}
