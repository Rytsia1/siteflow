package com.siteflow.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.IdempotencyKeyRecord;

@Mapper
public interface IdempotencyKeyMapper {

    @Insert("INSERT INTO idempotency_keys (key_value, user_id, endpoint, status, created_at) "
            + "VALUES (#{keyValue}, #{userId}, #{endpoint}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IdempotencyKeyRecord record);

    @Select("SELECT * FROM idempotency_keys WHERE key_value = #{keyValue}")
    IdempotencyKeyRecord findByKeyValue(@Param("keyValue") String keyValue);

    @Update("UPDATE idempotency_keys SET status = #{status} WHERE key_value = #{keyValue}")
    int updateStatus(@Param("keyValue") String keyValue, @Param("status") String status);

    @Delete("DELETE FROM idempotency_keys WHERE key_value = #{keyValue}")
    int deleteByKeyValue(@Param("keyValue") String keyValue);

    @Delete("DELETE FROM idempotency_keys WHERE created_at < #{cutoff}")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

    @Delete("DELETE FROM idempotency_keys")
    int deleteAll();
}
