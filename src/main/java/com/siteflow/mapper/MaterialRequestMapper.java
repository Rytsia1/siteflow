package com.siteflow.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.web.dto.MaterialRequestView;

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

    /**
     * Advances the MR through its lifecycle: DRAFT → SUBMITTED → APPROVED → PO_CREATED → COMPLETED.
     * Only matches while the MR is still in expectedStatus, so two concurrent transitions on the
     * same MR (e.g. two generate-PO calls) can't both succeed.
     */
    @Update("UPDATE material_requests SET status = #{newStatus} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") MaterialRequestStatus expectedStatus,
                     @Param("newStatus") MaterialRequestStatus newStatus);

    /**
     * Lists requests by status with the requester's name resolved, for the
     * procurement dashboard (avoids a raw-user-id-only table in the UI).
     */
    @Select("SELECT mr.id AS id, u.full_name AS requester_name, mr.justification AS justification, "
            + "mr.request_date AS request_date, mr.status AS status "
            + "FROM material_requests mr "
            + "JOIN users u ON u.id = mr.requested_by "
            + "WHERE mr.status = #{status} "
            + "ORDER BY mr.request_date")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "requester_name", javaType = String.class),
            @Arg(column = "justification", javaType = String.class),
            @Arg(column = "request_date", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = MaterialRequestStatus.class)
    })
    List<MaterialRequestView> findByStatusWithDetails(@Param("status") MaterialRequestStatus status);
}
