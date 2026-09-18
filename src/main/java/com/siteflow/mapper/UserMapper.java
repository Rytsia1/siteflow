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
            + "r.role_name AS role_name, u.is_active AS is_active, u.token_version AS token_version "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE u.username = #{username}")
    UserWithRole findByUsername(String username);

    @Select("SELECT u.id AS id, u.username AS username, u.password_hash AS password_hash, "
            + "r.role_name AS role_name, u.is_active AS is_active, u.token_version AS token_version "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE u.id = #{id}")
    UserWithRole findUserWithRoleById(Long id);

    @Select("SELECT id, role_id, username, password_hash, full_name, job_position, "
            + "is_active, token_version, deactivated_at, created_at, updated_at "
            + "FROM users WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.role_name = 'ADMIN' AND u.is_active = TRUE")
    int countActiveAdmins();

    @Select("SELECT id FROM roles WHERE role_name = #{roleName}")
    Long findRoleIdByName(String roleName);

    @Select("SELECT id, role_id, username, password_hash, full_name, job_position, "
            + "is_active, token_version, deactivated_at, created_at, updated_at "
            + "FROM users WHERE username = #{username}")
    User findUserEntityByUsername(String username);

    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active, token_version, created_at, updated_at) "
            + "VALUES (#{roleId}, #{username}, #{passwordHash}, #{fullName}, #{jobPosition}, #{isActive}, COALESCE(#{tokenVersion}, 1), #{createdAt}, #{updatedAt})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(User user);

    @Update("UPDATE users SET password_hash = #{passwordHash}, full_name = #{fullName}, "
            + "job_position = #{jobPosition}, is_active = TRUE, token_version = token_version + 1, "
            + "deactivated_at = NULL, updated_at = #{updatedAt} "
            + "WHERE id = #{id}")
    int updateProvisionedUser(User user);

    @Update("UPDATE users SET is_active = FALSE, full_name = #{anonymizedName}, job_position = NULL, "
            + "password_hash = #{scrambledHash}, token_version = token_version + 1, "
            + "deactivated_at = #{deactivatedAt}, updated_at = #{deactivatedAt} "
            + "WHERE id = #{id}")
    int deactivateAndAnonymize(
            @Param("id") Long id,
            @Param("anonymizedName") String anonymizedName,
            @Param("scrambledHash") String scrambledHash,
            @Param("deactivatedAt") LocalDateTime deactivatedAt);

    @Update("UPDATE users SET token_version = token_version + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementTokenVersion(Long id);

    @Update("UPDATE users SET role_id = #{roleId}, token_version = token_version + 1, updated_at = NOW() WHERE id = #{id}")
    int updateRoleAndIncrementTokenVersion(@Param("id") Long id, @Param("roleId") Long roleId);
}
