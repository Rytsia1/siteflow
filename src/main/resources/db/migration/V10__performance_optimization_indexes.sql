-- SiteFlow V10: Database Access and API Performance Optimization Indexes
-- 1. Accelerate borrow request user history lookups and admin approval queues (eliminate filesort)
-- 2. Accelerate material request status filtering and requester history (eliminate filesort)
-- 3. Accelerate monthly consumption trends and batch reorder recommendation queries on transaction_logs
-- 4. Accelerate inventory item search and filtering by code and name

-- 1. Borrow Requests
CREATE INDEX idx_borrow_requests_user_date ON borrow_requests (user_id, request_date);
CREATE INDEX idx_borrow_requests_approval_date ON borrow_requests (approval_status, request_date);

-- 2. Material Requests
CREATE INDEX idx_material_requests_status_date ON material_requests (status, request_date);
CREATE INDEX idx_material_requests_user_date ON material_requests (requested_by, request_date);

-- 3. Transaction Logs (covers item_id + transaction_type + timestamp)
CREATE INDEX idx_transaction_logs_item_type_ts ON transaction_logs (item_id, transaction_type, timestamp);

-- 4. Items (accelerates code + name lookups)
CREATE INDEX idx_items_code_name ON items (item_code, name);
