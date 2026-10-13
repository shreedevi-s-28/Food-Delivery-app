-- =========================================================
-- ONLINE FOOD DELIVERY - SQL COMMANDS
-- =========================================================

-- =========================================================
-- 1. DQL (DATA QUERY LANGUAGE)
-- =========================================================

-- 1. Full menu grouped by category
SELECT category, name, price
FROM food_items
WHERE available = 'Y'
ORDER BY category, name;

-- 2. Items cheaper than 150 rupees
SELECT name, price
FROM food_items
WHERE price < 150
ORDER BY price;

-- 3. Number of items and average price in each category
SELECT category, COUNT(*) AS items, ROUND(AVG(price), 2) AS avg_price
FROM food_items
GROUP BY category
ORDER BY category;

-- 4. Order history of one customer
SELECT o.order_id, o.order_date, c.full_name, f.name, oi.quantity, oi.unit_price,
       oi.quantity * oi.unit_price AS line_total, o.total_amount, o.status
FROM orders o
JOIN customers c ON c.customer_id = o.customer_id
JOIN order_items oi ON oi.order_id = o.order_id
JOIN food_items f ON f.item_id = oi.item_id
WHERE c.email = 'demo@example.com'
ORDER BY o.order_id DESC;

-- 5. Check order totals
SELECT o.order_id, o.total_amount, SUM(oi.quantity * oi.unit_price) AS calculated_total
FROM orders o
JOIN order_items oi ON oi.order_id = o.order_id
GROUP BY o.order_id, o.total_amount
HAVING o.total_amount <> SUM(oi.quantity * oi.unit_price);

-- 6. Most popular food items
SELECT f.name, SUM(oi.quantity) AS times_ordered
FROM order_items oi
JOIN food_items f ON f.item_id = oi.item_id
GROUP BY f.name
ORDER BY times_ordered DESC;

-- 7. Customers who have never placed an order
SELECT full_name, email
FROM customers
WHERE customer_id NOT IN (SELECT customer_id FROM orders);


-- =========================================================
-- 2. DML (DATA MANIPULATION LANGUAGE)
-- =========================================================

-- 8. Raise Drinks prices by 10 percent
UPDATE food_items
SET price = ROUND(price * 1.10, 2)
WHERE category = 'Drinks';

SELECT name, price FROM food_items WHERE category = 'Drinks';
ROLLBACK;

-- 9. Hide Egg Biryani from the menu
UPDATE food_items
SET available = 'N'
WHERE name = 'Egg Biryani';

SELECT name, available FROM food_items WHERE name = 'Egg Biryani';
ROLLBACK;

-- 10. Update order status
UPDATE orders
SET status = 'OUT_FOR_DELIVERY'
WHERE order_id = 1;

SELECT order_id, status FROM orders WHERE order_id = 1;
ROLLBACK;

-- 11. Change customer's address
UPDATE customers
SET address = 'Library Road, Near Main Gate'
WHERE email = 'demo@example.com';

SELECT email, address FROM customers WHERE email = 'demo@example.com';
ROLLBACK;

-- 12. Delete one line of an order
DELETE FROM order_items
WHERE order_id = 1
AND item_id = (SELECT item_id FROM food_items WHERE name = 'Gulab Jamun');

SELECT * FROM order_items WHERE order_id = 1;
ROLLBACK;

-- 13. Delete a whole order
DELETE FROM orders WHERE order_id = 1;

SELECT COUNT(*) AS lines_left FROM order_items WHERE order_id = 1;
ROLLBACK;

-- 14. Delete Margherita Pizza (may fail if referenced by an order)
DELETE FROM food_items WHERE name = 'Margherita Pizza';

-- 15. Delete Cheese Fries
DELETE FROM food_items WHERE name = 'Cheese Fries';

SELECT COUNT(*) AS rows_found FROM food_items WHERE name = 'Cheese Fries';
ROLLBACK;


-- =========================================================
-- 3. DDL (DATA DEFINITION LANGUAGE)
-- =========================================================

-- Add a column
-- ALTER TABLE food_items ADD (is_veg CHAR(1) DEFAULT 'Y' NOT NULL);

-- Drop a column
-- ALTER TABLE food_items DROP COLUMN is_veg;

-- Create a view
-- CREATE VIEW order_summary AS
-- SELECT o.order_id, c.full_name, o.order_date, o.total_amount, o.status
-- FROM orders o
-- JOIN customers c ON c.customer_id = o.customer_id;


-- =========================================================
-- 4. DCL (DATA CONTROL LANGUAGE)
-- =========================================================

-- Grant permission
-- GRANT SELECT ON food_items TO username;

-- Revoke permission
-- REVOKE SELECT ON food_items FROM username;


-- =========================================================
-- 5. TCL (TRANSACTION CONTROL LANGUAGE)
-- =========================================================

-- Undo uncommitted changes
-- ROLLBACK;

-- Save changes permanently
-- COMMIT;

-- Create a savepoint
-- SAVEPOINT before_update;
