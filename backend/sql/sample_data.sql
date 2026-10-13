-- =====================================================================
-- Online Food Delivery - sample data (DML: INSERT)
-- Run this after schema.sql, as the same user.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Menu (prices in rupees)
-- ---------------------------------------------------------------------
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Margherita Pizza',   'Tomato sauce, mozzarella and fresh basil on a thin crust.', 'Pizza', 249.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Paneer Tikka Pizza', 'Spiced paneer, onion and capsicum with mozzarella.',       'Pizza', 329.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Farmhouse Pizza',    'Mushroom, sweet corn, olives and capsicum.',                 'Pizza', 349.00);

INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Veg Burger',         'Crispy vegetable patty, lettuce and house sauce.',           'Burgers', 119.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Chicken Burger',     'Grilled chicken patty with cheese and mayo.',                'Burgers', 169.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Cheese Fries',       'Golden fries topped with melted cheese sauce.',              'Burgers',  99.00);

INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Veg Biryani',        'Basmati rice cooked with vegetables and whole spices.',      'Biryani', 179.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Chicken Biryani',    'Dum-style biryani with tender chicken and raita.',           'Biryani', 229.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Egg Biryani',        'Basmati rice layered with masala-coated boiled eggs.',       'Biryani', 189.00);

INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Idli Sambar',        'Four soft idlis with sambar and two chutneys.',              'South Indian',  70.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Masala Dosa',        'Crisp dosa filled with spiced potato, served with chutney.', 'South Indian',  95.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Filter Coffee',      'South Indian style coffee with frothed milk.',               'Drinks',  40.00);

INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Fresh Lime Soda',    'Sweet or salted, chilled.',                                  'Drinks',  50.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Mango Lassi',        'Thick yogurt shake blended with mango pulp.',                'Drinks',  80.00);

INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Gulab Jamun',        'Two warm milk dumplings in rose-scented syrup.',             'Desserts',  99.00);
INSERT INTO food_items (item_id, name, description, category, price) VALUES (food_items_seq.NEXTVAL, 'Chocolate Brownie',  'Fudgy brownie served warm.',                                 'Desserts', 129.00);

-- ---------------------------------------------------------------------
-- One demo customer.  Email: demo@example.com   Password: Demo@1234
-- The value below is a salted PBKDF2 hash made by the Java backend (Security.java),
-- not the password itself. New customers should register through the website.
-- ---------------------------------------------------------------------
INSERT INTO customers (customer_id, full_name, email, phone, address, password_hash)
VALUES (customers_seq.NEXTVAL, 'Demo Student', 'demo@example.com', '9876543210',
        'Hostel Block B, Room 204, College Campus',
        '120000:0x2exiu2xl/19u4DsxrApA==:nmvYx4rQjYl2lW3UOIkRda8XvVXtWUFB4k414aRHisI=');

-- ---------------------------------------------------------------------
-- One demo order for that customer: 2 x Margherita Pizza + 1 x Gulab Jamun
-- total = 2 * 249.00 + 99.00 = 597.00
-- (orders_seq.CURRVAL is the id that orders_seq.NEXTVAL just produced)
-- ---------------------------------------------------------------------
INSERT INTO orders (order_id, customer_id, total_amount, status, delivery_address)
VALUES (orders_seq.NEXTVAL,
        (SELECT customer_id FROM customers WHERE email = 'demo@example.com'),
        597.00, 'DELIVERED', 'Hostel Block B, Room 204, College Campus');

INSERT INTO order_items (order_item_id, order_id, item_id, quantity, unit_price)
VALUES (order_items_seq.NEXTVAL, orders_seq.CURRVAL,
        (SELECT item_id FROM food_items WHERE name = 'Margherita Pizza'), 2,
        (SELECT price   FROM food_items WHERE name = 'Margherita Pizza'));

INSERT INTO order_items (order_item_id, order_id, item_id, quantity, unit_price)
VALUES (order_items_seq.NEXTVAL, orders_seq.CURRVAL,
        (SELECT item_id FROM food_items WHERE name = 'Gulab Jamun'), 1,
        (SELECT price   FROM food_items WHERE name = 'Gulab Jamun'));

COMMIT;
