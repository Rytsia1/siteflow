package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.Item;
import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.web.dto.ItemSummaryView;

@Mapper
public interface ItemMapper {

    @Select("SELECT * FROM items ORDER BY id")
    List<Item> findAll();

    @Select("SELECT * FROM items WHERE id = #{id}")
    Item findById(Long id);

    @Select("SELECT i.id AS id, i.item_code AS item_code, i.name AS name, i.category AS category, "
            + "i.unit AS unit, i.min_stock_threshold AS min_stock_threshold, "
            + "COALESCE(SUM(s.current_qty), 0) AS total_qty "
            + "FROM items i LEFT JOIN item_stocks s ON s.item_id = i.id "
            + "GROUP BY i.id, i.item_code, i.name, i.category, i.unit, i.min_stock_threshold "
            + "ORDER BY i.id")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "item_code", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "category", javaType = ItemCategory.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "min_stock_threshold", javaType = Integer.class),
            @Arg(column = "total_qty", javaType = Integer.class)
    })
    List<ItemSummaryView> findAllWithStock();
}
