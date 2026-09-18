package com.siteflow.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.TransactionLog;
import com.siteflow.web.dto.AuditLogView;

@Mapper
public interface TransactionLogMapper {

    @Insert("INSERT INTO transaction_logs (item_id, location_id, user_id, transaction_type, qty_change, "
            + "reference_id, timestamp, action, resource_type, resource_id, actor_username, status, "
            + "before_state, after_state, details, ip_address) "
            + "VALUES (#{itemId}, #{locationId}, #{userId}, #{transactionType}, #{qtyChange}, "
            + "#{referenceId}, #{timestamp}, #{action}, #{resourceType}, #{resourceId}, #{actorUsername}, "
            + "#{status}, #{beforeState}, #{afterState}, #{details}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TransactionLog log);

    @Select("<script>"
            + "SELECT t.id, t.timestamp, t.action, t.resource_type AS resourceType, "
            + "t.resource_id AS resourceId, "
            + "COALESCE(t.actor_username, u.username, 'SYSTEM') AS actorUsername, "
            + "t.user_id AS userId, t.status, t.before_state AS beforeState, "
            + "t.after_state AS afterState, t.details, t.ip_address AS ipAddress "
            + "FROM transaction_logs t "
            + "LEFT JOIN users u ON u.id = t.user_id "
            + "<where>"
            + "  <if test='action != null and action != \"\"'> AND t.action = #{action} </if>"
            + "  <if test='resourceType != null and resourceType != \"\"'> AND t.resource_type = #{resourceType} </if>"
            + "  <if test='resourceId != null'> AND t.resource_id = #{resourceId} </if>"
            + "  <if test='actor != null and actor != \"\"'> AND (t.actor_username LIKE CONCAT('%', #{actor}, '%') OR u.username LIKE CONCAT('%', #{actor}, '%')) </if>"
            + "  <if test='status != null and status != \"\"'> AND t.status = #{status} </if>"
            + "  <if test='startDate != null'> AND t.timestamp &gt;= #{startDate} </if>"
            + "  <if test='endDate != null'> AND t.timestamp &lt;= #{endDate} </if>"
            + "</where>"
            + "ORDER BY t.timestamp DESC, t.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AuditLogView> findAuditLogsPaged(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("actor") String actor,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Select("<script>"
            + "SELECT COUNT(*) "
            + "FROM transaction_logs t "
            + "LEFT JOIN users u ON u.id = t.user_id "
            + "<where>"
            + "  <if test='action != null and action != \"\"'> AND t.action = #{action} </if>"
            + "  <if test='resourceType != null and resourceType != \"\"'> AND t.resource_type = #{resourceType} </if>"
            + "  <if test='resourceId != null'> AND t.resource_id = #{resourceId} </if>"
            + "  <if test='actor != null and actor != \"\"'> AND (t.actor_username LIKE CONCAT('%', #{actor}, '%') OR u.username LIKE CONCAT('%', #{actor}, '%')) </if>"
            + "  <if test='status != null and status != \"\"'> AND t.status = #{status} </if>"
            + "  <if test='startDate != null'> AND t.timestamp &gt;= #{startDate} </if>"
            + "  <if test='endDate != null'> AND t.timestamp &lt;= #{endDate} </if>"
            + "</where>"
            + "</script>")
    int countAuditLogs(
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("actor") String actor,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Select("SELECT t.id, t.timestamp, t.action, t.resource_type AS resourceType, "
            + "t.resource_id AS resourceId, "
            + "COALESCE(t.actor_username, u.username, 'SYSTEM') AS actorUsername, "
            + "t.user_id AS userId, t.status, t.before_state AS beforeState, "
            + "t.after_state AS afterState, t.details, t.ip_address AS ipAddress "
            + "FROM transaction_logs t "
            + "LEFT JOIN users u ON u.id = t.user_id "
            + "WHERE t.id = #{id}")
    AuditLogView findAuditLogById(@Param("id") Long id);
}
