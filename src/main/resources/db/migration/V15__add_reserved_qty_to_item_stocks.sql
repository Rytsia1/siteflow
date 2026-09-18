-- V15: Add reserved_qty to item_stocks for borrow stock reservation architecture
-- current_qty: available stock for allocation
-- reserved_qty: stock allocated to active pending/approved borrow requests
-- physical stock: current_qty + reserved_qty

ALTER TABLE item_stocks
    ADD COLUMN reserved_qty INT NOT NULL DEFAULT 0 AFTER current_qty;

ALTER TABLE item_stocks
    ADD CONSTRAINT chk_item_stocks_reserved_qty CHECK (reserved_qty >= 0);
