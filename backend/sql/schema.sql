-- =====================================================================
-- Online Food Delivery - Oracle schema (DDL)
-- Run this first, connected as the application user (see README).
-- Needs Oracle 12c or newer (XE 21c and Free 23ai both work).
-- =====================================================================

-- To start again from scratch, run these lines first (errors about
-- "table or view does not exist" on a fresh schema are harmless):
--
-- DROP TABLE order_items;
-- DROP TABLE orders;
-- DROP TABLE food_items;
-- DROP TABLE customers;
-- DROP SEQUENCE order_items_seq;
-- DROP SEQUENCE orders_seq;
-- DROP SEQUENCE food_items_seq;
-- DROP SEQUENCE customers_seq;

-- ---------------------------------------------------------------------
-- Sequences: they generate the primary key values (1, 2, 3, ...)
-- ---------------------------------------------------------------------
CREATE SEQUENCE customers_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE food_items_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE orders_seq      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE order_items_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---------------------------------------------------------------------
-- customers: one row per registered user.
-- password_hash holds a salted PBKDF2 hash, never the real password.
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    customer_id    NUMBER(10)     CONSTRAINT pk_customers PRIMARY KEY,
    full_name      VARCHAR2(100)  NOT NULL,
    email          VARCHAR2(150)  NOT NULL CONSTRAINT uq_customers_email UNIQUE,
    phone          VARCHAR2(15)   NOT NULL,
    address        VARCHAR2(250),
    password_hash  VARCHAR2(200)  NOT NULL,
    created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_customers_email CHECK (email = LOWER(email)),
    CONSTRAINT ck_customers_phone CHECK (REGEXP_LIKE(phone, '^[0-9]{10}$'))
);

-- ---------------------------------------------------------------------
-- food_items: the menu. The backend reads prices from here.
-- ---------------------------------------------------------------------
CREATE TABLE food_items (
    item_id      NUMBER(10)     CONSTRAINT pk_food_items PRIMARY KEY,
    name         VARCHAR2(100)  NOT NULL CONSTRAINT uq_food_items_name UNIQUE,
    description  VARCHAR2(250),
    category     VARCHAR2(50)   NOT NULL,
    price        NUMBER(8,2)    NOT NULL CONSTRAINT ck_food_items_price CHECK (price > 0),
    available    CHAR(1)        DEFAULT 'Y' NOT NULL
                                CONSTRAINT ck_food_items_available CHECK (available IN ('Y', 'N'))
);

-- ---------------------------------------------------------------------
-- orders: one row per order placed by a customer.
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    order_id          NUMBER(10)    CONSTRAINT pk_orders PRIMARY KEY,
    customer_id       NUMBER(10)    NOT NULL
                                    CONSTRAINT fk_orders_customer REFERENCES customers (customer_id),
    order_date        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    total_amount      NUMBER(10,2)  NOT NULL CONSTRAINT ck_orders_total CHECK (total_amount >= 0),
    status            VARCHAR2(20)  DEFAULT 'PLACED' NOT NULL
                                    CONSTRAINT ck_orders_status CHECK
                                    (status IN ('PLACED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')),
    delivery_address  VARCHAR2(250) NOT NULL
);

-- ---------------------------------------------------------------------
-- order_items: the lines of an order (which food, how many, at what price).
-- unit_price is copied from food_items when the order is placed, so old
-- orders keep their original price even if the menu price changes later.
-- Deleting an order also deletes its lines (ON DELETE CASCADE).
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    order_item_id  NUMBER(10)   CONSTRAINT pk_order_items PRIMARY KEY,
    order_id       NUMBER(10)   NOT NULL
                                CONSTRAINT fk_order_items_order REFERENCES orders (order_id) ON DELETE CASCADE,
    item_id        NUMBER(10)   NOT NULL
                                CONSTRAINT fk_order_items_item REFERENCES food_items (item_id),
    quantity       NUMBER(3)    NOT NULL CONSTRAINT ck_order_items_qty CHECK (quantity BETWEEN 1 AND 20),
    unit_price     NUMBER(8,2)  NOT NULL CONSTRAINT ck_order_items_price CHECK (unit_price > 0)
);

-- Indexes on foreign keys make "orders of a customer" and "items of an order" fast.
CREATE INDEX idx_orders_customer     ON orders (customer_id);
CREATE INDEX idx_order_items_order   ON order_items (order_id);
CREATE INDEX idx_order_items_item    ON order_items (item_id);
