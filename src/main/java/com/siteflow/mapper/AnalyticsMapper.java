package com.siteflow.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.web.dto.ConsumptionTrendView;
import com.siteflow.web.dto.ItemMonthlyConsumptionView;
import com.siteflow.web.dto.ItemSummaryView;
import com.siteflow.web.dto.MonthlyConsumptionView;
import com.siteflow.web.dto.MostBorrowedItemView;
import com.siteflow.web.dto.ToolUtilizationView;

@Mapper
public interface AnalyticsMapper {

    /** Items whose total stock across all locations has fallen to or below their reorder threshold. */
    @Select("SELECT i.id AS id, i.item_code AS item_code, i.name AS name, i.category AS category, "
            + "i.unit AS unit, i.min_stock_threshold AS min_stock_threshold, "
            + "COALESCE(SUM(s.current_qty), 0) AS total_qty "
            + "FROM items i LEFT JOIN item_stocks s ON s.item_id = i.id "
            + "GROUP BY i.id, i.item_code, i.name, i.category, i.unit, i.min_stock_threshold "
            + "HAVING total_qty <= i.min_stock_threshold "
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
    List<ItemSummaryView> findLowStockItems();

    /**
     * Efficient scalar count of items currently at or below their reorder threshold.
     * Avoids loading all low-stock item entities and materializing DTOs just for dashboard counters.
     */
    @Select("SELECT COUNT(*) FROM ("
            + "  SELECT i.id "
            + "  FROM items i LEFT JOIN item_stocks s ON s.item_id = i.id "
            + "  GROUP BY i.id, i.min_stock_threshold "
            + "  HAVING COALESCE(SUM(s.current_qty), 0) <= i.min_stock_threshold"
            + ") AS low_stock")
    int countLowStockItems();

    /**
     * Monthly outflow of consumable items (borrowed quantity) within the given date range,
     * derived from the signed qty_change already recorded on BORROW transaction logs.
     */
    @Select("SELECT DATE_FORMAT(t.timestamp, '%Y-%m') AS period, SUM(-t.qty_change) AS total_outflow "
            + "FROM transaction_logs t "
            + "JOIN items i ON i.id = t.item_id "
            + "WHERE t.transaction_type = 'BORROW' AND i.category = 'CONSUMABLE' "
            + "AND t.timestamp BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY period "
            + "ORDER BY period")
    List<ConsumptionTrendView> findConsumptionTrends(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Per-tool-item ratio of physical instances currently out on a non-completed borrow
     * versus the total number of physical instances owned.
     * Optimized using pre-aggregated LEFT JOINs instead of correlated subqueries.
     */
    @Select("SELECT i.id AS item_id, i.name AS item_name, "
            + "COALESCE(inst.total_owned, 0) AS total_owned, "
            + "COALESCE(act.currently_out, 0) AS currently_out "
            + "FROM items i "
            + "LEFT JOIN ("
            + "  SELECT item_id, COUNT(*) AS total_owned "
            + "  FROM item_instances "
            + "  GROUP BY item_id"
            + ") inst ON inst.item_id = i.id "
            + "LEFT JOIN ("
            + "  SELECT bi.item_id, SUM(bi.qty_borrowed - bi.qty_returned) AS currently_out "
            + "  FROM borrow_items bi "
            + "  JOIN borrow_requests br ON br.id = bi.borrow_request_id "
            + "  WHERE br.status <> 'COMPLETED' "
            + "  GROUP BY bi.item_id"
            + ") act ON act.item_id = i.id "
            + "WHERE i.category = 'TOOL' "
            + "ORDER BY i.id")
    List<ToolUtilizationView> findToolUtilization();

    /** Count of borrow requests not yet fully completed (still holding items out). */
    @Select("SELECT COUNT(*) FROM borrow_requests WHERE status <> 'COMPLETED'")
    int countActiveBorrows();

    /** The single item with the highest borrowed quantity within the given date range, if any. */
    @Select("SELECT t.item_id AS item_id, i.name AS item_name, SUM(-t.qty_change) AS total_qty "
            + "FROM transaction_logs t "
            + "JOIN items i ON i.id = t.item_id "
            + "WHERE t.transaction_type = 'BORROW' AND t.timestamp BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY t.item_id, i.name "
            + "ORDER BY total_qty DESC "
            + "LIMIT 1")
    MostBorrowedItemView findMostBorrowedItemForPeriod(@Param("startDate") LocalDateTime startDate,
                                                         @Param("endDate") LocalDateTime endDate);

    /** Borrowed quantity per month for one item, most recent month first, capped at the last 6 months. */
    @Select("SELECT DATE_FORMAT(timestamp, '%Y-%m') AS period, SUM(-qty_change) AS qty "
            + "FROM transaction_logs "
            + "WHERE item_id = #{itemId} AND transaction_type = 'BORROW' "
            + "GROUP BY period "
            + "ORDER BY period DESC "
            + "LIMIT 6")
    List<MonthlyConsumptionView> findMonthlyConsumptionByItem(@Param("itemId") Long itemId);

    /**
     * Batch lookup of borrowed quantity per month for multiple items, ordered most recent first.
     * Eliminates N+1 query execution when generating reorder recommendations.
     */
    @Select("<script>"
            + "SELECT item_id AS itemId, DATE_FORMAT(timestamp, '%Y-%m') AS period, SUM(-qty_change) AS qty "
            + "FROM transaction_logs "
            + "WHERE transaction_type = 'BORROW' "
            + "AND item_id IN "
            + "<foreach item='id' collection='itemIds' open='(' separator=',' close=')'>#{id}</foreach> "
            + "<if test='since != null'>"
            + "AND timestamp &gt;= #{since} "
            + "</if>"
            + "GROUP BY item_id, period "
            + "ORDER BY item_id, period DESC"
            + "</script>")
    List<ItemMonthlyConsumptionView> findRecentMonthlyConsumptionForItems(
            @Param("itemIds") List<Long> itemIds,
            @Param("since") LocalDateTime since);
}
