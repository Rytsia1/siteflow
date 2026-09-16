package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.MaterialRequestItem;

@Mapper
public interface MaterialRequestItemMapper {

    @Insert("INSERT INTO material_request_items (mr_id, item_id, requested_qty) "
            + "VALUES (#{mrId}, #{itemId}, #{requestedQty})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MaterialRequestItem item);

    @Select("SELECT * FROM material_request_items WHERE mr_id = #{mrId}")
    List<MaterialRequestItem> findByMrId(@Param("mrId") Long mrId);
}
