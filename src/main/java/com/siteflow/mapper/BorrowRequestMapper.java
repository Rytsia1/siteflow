package com.siteflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.BorrowStatus;

@Mapper
public interface BorrowRequestMapper {

    @Insert("INSERT INTO borrow_requests (user_id, location_id, request_date, status) "
            + "VALUES (#{userId}, #{locationId}, #{requestDate}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BorrowRequest request);

    @Select("SELECT * FROM borrow_requests WHERE id = #{id}")
    BorrowRequest findById(Long id);

    @Update("UPDATE borrow_requests SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") BorrowStatus status);
}
