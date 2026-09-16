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

    @Insert("INSERT INTO item_instances (item_id, serial_number, qr_code_value, tool_condition) "
            + "VALUES (#{itemId}, #{serialNumber}, #{qrCodeValue}, #{toolCondition})")
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
     * Updates the physical condition of a tool after a return inspection.
     * Called immediately after a return is processed so the warehouse knows
     * if a returned tool needs repair before it can be re-issued.
     */
    @Update("UPDATE item_instances SET tool_condition = #{condition} WHERE id = #{id}")
    int updateToolCondition(@Param("id") Long id, @Param("condition") ToolCondition condition);
}
