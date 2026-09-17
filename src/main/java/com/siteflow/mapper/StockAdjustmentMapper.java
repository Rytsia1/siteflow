package com.siteflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import com.siteflow.domain.StockAdjustment;

@Mapper
public interface StockAdjustmentMapper {

    @Insert("INSERT INTO stock_adjustments (item_id, location_id, adjusted_by, adjustment_type, qty, reason, created_at) "
            + "VALUES (#{itemId}, #{locationId}, #{adjustedBy}, #{adjustmentType}, #{qty}, #{reason}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StockAdjustment adjustment);
}
