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

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.web.dto.BorrowRequestView;

@Mapper
public interface BorrowRequestMapper {

    @Insert("INSERT INTO borrow_requests (user_id, location_id, request_date, status, approval_status) "
            + "VALUES (#{userId}, #{locationId}, #{requestDate}, #{status}, #{approvalStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BorrowRequest request);

    @Select("SELECT * FROM borrow_requests WHERE id = #{id}")
    BorrowRequest findById(Long id);

    @Update("UPDATE borrow_requests SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") BorrowStatus status);

    /**
     * Records an approval decision: sets the approval_status (APPROVED or REJECTED),
     * the id of the admin who decided, and an optional explanatory note, all in one
     * atomic write so no partial approval state is ever persisted.
     */
    @Update("UPDATE borrow_requests "
            + "SET approval_status = #{approvalStatus}, approved_by = #{approvedBy}, approval_note = #{note} "
            + "WHERE id = #{id}")
    int updateApproval(@Param("id") Long id,
                       @Param("approvalStatus") ApprovalStatus approvalStatus,
                       @Param("approvedBy") Long approvedBy,
                       @Param("note") String note);

    /**
     * Lists requests by approval status with requester/location names resolved,
     * for the admin approval dashboard (avoids a raw-id-only table in the UI).
     */
    @Select("SELECT br.id AS id, u.full_name AS requester_name, l.location_name AS location_name, "
            + "br.request_date AS request_date, br.status AS status, br.approval_status AS approval_status "
            + "FROM borrow_requests br "
            + "JOIN users u ON u.id = br.user_id "
            + "JOIN locations l ON l.id = br.location_id "
            + "WHERE br.approval_status = #{approvalStatus} "
            + "ORDER BY br.request_date")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "requester_name", javaType = String.class),
            @Arg(column = "location_name", javaType = String.class),
            @Arg(column = "request_date", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = BorrowStatus.class),
            @Arg(column = "approval_status", javaType = ApprovalStatus.class)
    })
    List<BorrowRequestView> findByApprovalStatusWithDetails(@Param("approvalStatus") ApprovalStatus approvalStatus);
}

