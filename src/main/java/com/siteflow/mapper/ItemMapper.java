package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    /**
     * Database-level pagination with filtering and dynamic sorting.
     * sortColumn and sortDirection MUST be validated against an explicit whitelist before calling.
     */
    @Select("<script>"
            + "SELECT i.id AS id, i.item_code AS item_code, i.name AS name, i.category AS category, "
            + "i.unit AS unit, i.min_stock_threshold AS min_stock_threshold, "
            + "COALESCE(SUM(s.current_qty), 0) AS total_qty "
            + "FROM items i LEFT JOIN item_stocks s ON s.item_id = i.id "
            + "<where>"
            + "  <if test='category != null'>"
            + "    AND i.category = #{category}"
            + "  </if>"
            + "  <if test='search != null and search != \"\"'>"
            + "    AND (i.item_code LIKE CONCAT('%', #{search}, '%') OR i.name LIKE CONCAT('%', #{search}, '%'))"
            + "  </if>"
            + "</where>"
            + "GROUP BY i.id, i.item_code, i.name, i.category, i.unit, i.min_stock_threshold "
            + "ORDER BY ${sortColumn} ${sortDirection} "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "item_code", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "category", javaType = ItemCategory.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "min_stock_threshold", javaType = Integer.class),
            @Arg(column = "total_qty", javaType = Integer.class)
    })
    List<ItemSummaryView> findWithStockPaged(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("category") ItemCategory category,
            @Param("search") String search,
            @Param("sortColumn") String sortColumn,
            @Param("sortDirection") String sortDirection);

    @Select("<script>"
            + "SELECT COUNT(*) FROM items i "
            + "<where>"
            + "  <if test='category != null'>"
            + "    AND i.category = #{category}"
            + "  </if>"
            + "  <if test='search != null and search != \"\"'>"
            + "    AND (i.item_code LIKE CONCAT('%', #{search}, '%') OR i.name LIKE CONCAT('%', #{search}, '%'))"
            + "  </if>"
            + "</where>"
            + "</script>")
    int countWithStock(
            @Param("category") ItemCategory category,
            @Param("search") String search);
}
