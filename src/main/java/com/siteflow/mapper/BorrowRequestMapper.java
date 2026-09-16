package com.siteflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;

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
}

