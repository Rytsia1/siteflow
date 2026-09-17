package com.siteflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.enums.ToolCondition;

@Mapper
public interface ItemInstanceMapper {

    @Insert("INSERT INTO item_instances (item_id, serial_number, qr_code_value, tool_condition, is_available, current_borrow_request_id) "
            + "VALUES (#{itemId}, #{serialNumber}, #{qrCodeValue}, #{toolCondition}, COALESCE(#{isAvailable}, TRUE), #{currentBorrowRequestId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ItemInstance instance);

    @Select("SELECT * FROM item_instances WHERE id = #{id}")
    ItemInstance findById(Long id);

    /** Used during checkout to locate the physical tool being assigned. */
    @Select("SELECT * FROM item_instances WHERE serial_number = #{serialNumber}")
    ItemInstance findBySerialNumber(String serialNumber);

    /** Used when a worker scans a QR code to identify the tool. */
    @Select("SELECT * FROM item_instances WHERE qr_code_value = #{qrCodeValue}")
    ItemInstance findByQrCodeValue(String qrCodeValue);

    /**
     * Atomically locks a tool instance to an active borrow request.
     * Guards against concurrent checkouts and guarantees that only tools currently available
     * and in GOOD condition can be borrowed.
     */
    @Update("UPDATE item_instances "
            + "SET is_available = FALSE, current_borrow_request_id = #{borrowRequestId} "
            + "WHERE id = #{id} AND is_available = TRUE AND tool_condition = 'GOOD'")
    int assignToBorrowRequest(@Param("id") Long id, @Param("borrowRequestId") Long borrowRequestId);

    /**
     * Atomically processes tool return: updates condition, sets availability, and clears active borrow request.
     * Guards against duplicate returns by ensuring the tool is currently borrowed.
     */
    @Update("UPDATE item_instances "
            + "SET tool_condition = #{condition}, is_available = #{isAvailable}, current_borrow_request_id = NULL "
            + "WHERE id = #{id} AND current_borrow_request_id IS NOT NULL")
    int releaseReturn(@Param("id") Long id, @Param("condition") ToolCondition condition,
            @Param("isAvailable") boolean isAvailable);

    /**
     * Updates the physical condition of a tool after inspection or repair.
     */
    @Update("UPDATE item_instances SET tool_condition = #{condition} WHERE id = #{id}")
    int updateToolCondition(@Param("id") Long id, @Param("condition") ToolCondition condition);

    /** Retrieves all tool instances currently checked out against a borrow request. */
    @Select("SELECT * FROM item_instances WHERE current_borrow_request_id = #{borrowRequestId}")
    java.util.List<ItemInstance> findByCurrentBorrowRequestId(Long borrowRequestId);
}
