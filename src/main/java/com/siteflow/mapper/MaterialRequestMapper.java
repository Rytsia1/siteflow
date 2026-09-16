package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.enums.MaterialRequestStatus;

@Mapper
public interface MaterialRequestMapper {

    @Insert("INSERT INTO material_requests (requested_by, request_date, status, justification) "
            + "VALUES (#{requestedBy}, #{requestDate}, #{status}, #{justification})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MaterialRequest request);

    @Select("SELECT * FROM material_requests WHERE id = #{id}")
    MaterialRequest findById(Long id);

    @Select("SELECT * FROM material_requests WHERE requested_by = #{userId} ORDER BY request_date DESC")
    List<MaterialRequest> findByRequestedBy(Long userId);

    @Select("SELECT * FROM material_requests WHERE status = #{status} ORDER BY request_date DESC")
    List<MaterialRequest> findByStatus(@Param("status") MaterialRequestStatus status);

    /** Advances the MR through its lifecycle: DRAFT → SUBMITTED → APPROVED → PO_CREATED → COMPLETED. */
    @Update("UPDATE material_requests SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") MaterialRequestStatus status);
}
