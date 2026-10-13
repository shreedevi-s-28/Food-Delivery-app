import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The logic behind every API endpoint. All SQL lives here and uses
 * PreparedStatement (placeholders "?") so user input can never change the query.
 */
public class Api {

    /** An error we want to show to the user, with the HTTP status to send. */
    public static class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{10}$");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    // ------------------------------------------------------------------
    // POST /api/register
    // ------------------------------------------------------------------
    public static Object register(Map<String, Object> body) throws SQLException {
        String name = text(body, "fullName");
        String email = text(body, "email").toLowerCase(Locale.ROOT);
        String phone = text(body, "phone");
        String address = text(body, "address");
        String password = rawText(body, "password"); // passwords are not trimmed

        if (name.length() < 2 || name.length() > 100) {
            bad("Name must be 2 to 100 characters long.");
        }
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            bad("Please enter a valid email address.");
        }
        if (!PHONE.matcher(phone).matches()) {
            bad("Phone number must be exactly 10 digits.");
        }
        if (address.length() > 250) {
            bad("Address can be at most 250 characters long.");
        }
        checkPassword(password);

        String sql = "INSERT INTO customers (customer_id, full_name, email, phone, address, password_hash) "
                + "VALUES (customers_seq.NEXTVAL, ?, ?, ?, ?, ?)";
        try (Connection con = Db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, address);
            ps.setString(5, Security.hashPassword(password));
            ps.executeUpdate(); // the connection auto-commits
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) { // ORA-00001: unique constraint violated
                throw new ApiException(409, "An account with this email already exists. Please log in.");
            }
            throw e;
        }
        return message("Account created. You can now log in.");
    }

    // ------------------------------------------------------------------
    // POST /api/login
    // ------------------------------------------------------------------
    public static Object login(Map<String, Object> body) throws SQLException {
        String email = text(body, "email").toLowerCase(Locale.ROOT);
        String password = rawText(body, "password");
        if (email.isEmpty() || password.isEmpty()) {
            bad("Please enter your email and password.");
        }

        String sql = "SELECT customer_id, full_name, email, phone, address, password_hash "
                + "FROM customers WHERE email = ?";
        try (Connection con = Db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                // Same message for "no such email" and "wrong password" on purpose.
                if (!rs.next() || !Security.verifyPassword(password, rs.getString("password_hash"))) {
                    throw new ApiException(401, "Incorrect email or password.");
                }
                Map<String, Object> customer = new LinkedHashMap<>();
                customer.put("name", rs.getString("full_name"));
                customer.put("email", rs.getString("email"));
                customer.put("phone", rs.getString("phone"));
                customer.put("address", rs.getString("address"));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("token", Security.createSession(rs.getLong("customer_id")));
                result.put("customer", customer);
                return result;
            }
        }
    }

    // ------------------------------------------------------------------
    // GET /api/menu
    // ------------------------------------------------------------------
    public static Object menu() throws SQLException {
        String sql = "SELECT item_id, name, description, category, price "
                + "FROM food_items WHERE available = 'Y' ORDER BY category, name";
        List<Object> items = new ArrayList<>();
        try (Connection con = Db.connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getLong("item_id"));
                item.put("name", rs.getString("name"));
                item.put("description", rs.getString("description"));
                item.put("category", rs.getString("category"));
                item.put("price", rs.getBigDecimal("price"));
                items.add(item);
            }
        }
        return items;
    }

    // ------------------------------------------------------------------
    // POST /api/orders  (login required)
    // The browser sends only item ids and quantities. Prices are read from
    // Oracle here, and the total is calculated here.
    // ------------------------------------------------------------------
    public static Object placeOrder(long customerId, Map<String, Object> body) throws SQLException {
        String address = text(body, "address");
        if (address.length() < 5 || address.length() > 250) {
            bad("Delivery address must be 5 to 250 characters long.");
        }

        // 1. Check the cart that the browser sent.
        Object rawItems = body.get("items");
        if (!(rawItems instanceof List) || ((List<?>) rawItems).isEmpty()) {
            bad("Your cart is empty.");
        }
        List<?> cart = (List<?>) rawItems;
        if (cart.size() > 30) {
            bad("An order can have at most 30 different items.");
        }
        Map<Long, Long> wanted = new LinkedHashMap<>(); // item id -> quantity
        for (Object entry : cart) {
            if (!(entry instanceof Map)) {
                bad("Invalid cart item.");
            }
            Map<?, ?> row = (Map<?, ?>) entry;
            Long itemId = wholeNumber(row.get("itemId"));
            Long quantity = wholeNumber(row.get("quantity"));
            if (itemId == null || quantity == null || quantity < 1 || quantity > 20) {
                bad("Each item needs a valid id and a quantity between 1 and 20.");
            }
            long merged = wanted.containsKey(itemId) ? wanted.get(itemId) + quantity : quantity;
            if (merged > 20) {
                bad("You can order at most 20 of the same item.");
            }
            wanted.put(itemId, merged);
        }

        // 2. Save everything as ONE transaction: all rows are saved, or none.
        try (Connection con = Db.connect()) {
            con.setAutoCommit(false);
            try {
                // 2a. Look up trusted prices in Oracle and calculate the total.
                List<long[]> ids = new ArrayList<>();      // {itemId, quantity}
                List<BigDecimal> prices = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                String priceSql = "SELECT price FROM food_items WHERE item_id = ? AND available = 'Y'";
                try (PreparedStatement ps = con.prepareStatement(priceSql)) {
                    for (Map.Entry<Long, Long> e : wanted.entrySet()) {
                        ps.setLong(1, e.getKey());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new ApiException(400,
                                        "One of the items is no longer available. Please refresh the menu.");
                            }
                            BigDecimal price = rs.getBigDecimal("price");
                            ids.add(new long[] {e.getKey(), e.getValue()});
                            prices.add(price);
                            total = total.add(price.multiply(BigDecimal.valueOf(e.getValue())));
                        }
                    }
                }

                // 2b. Get a new order id from the sequence.
                long orderId;
                try (PreparedStatement ps = con.prepareStatement("SELECT orders_seq.NEXTVAL FROM dual");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    orderId = rs.getLong(1);
                }

                // 2c. Insert the order row.
                String orderSql = "INSERT INTO orders (order_id, customer_id, total_amount, delivery_address) "
                        + "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(orderSql)) {
                    ps.setLong(1, orderId);
                    ps.setLong(2, customerId);
                    ps.setBigDecimal(3, total);
                    ps.setString(4, address);
                    ps.executeUpdate();
                }

                // 2d. Insert one order_items row per cart line (the price is copied into the row).
                String lineSql = "INSERT INTO order_items (order_item_id, order_id, item_id, quantity, unit_price) "
                        + "VALUES (order_items_seq.NEXTVAL, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(lineSql)) {
                    for (int i = 0; i < ids.size(); i++) {
                        ps.setLong(1, orderId);
                        ps.setLong(2, ids.get(i)[0]);
                        ps.setLong(3, ids.get(i)[1]);
                        ps.setBigDecimal(4, prices.get(i));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("orderId", orderId);
                result.put("total", total);
                result.put("message", "Order placed successfully.");
                return result;
            } catch (SQLException | RuntimeException e) {
                con.rollback(); // undo everything if anything went wrong
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------
    // GET /api/orders  (login required) - order history of the logged-in customer
    // ------------------------------------------------------------------
    public static Object orderHistory(long customerId) throws SQLException {
        String sql = "SELECT o.order_id, o.order_date, o.total_amount, o.status, o.delivery_address, "
                + "       f.name, oi.quantity, oi.unit_price "
                + "FROM orders o "
                + "JOIN order_items oi ON oi.order_id = o.order_id "
                + "JOIN food_items f ON f.item_id = oi.item_id "
                + "WHERE o.customer_id = ? "
                + "ORDER BY o.order_id DESC, oi.order_item_id";

        Map<Long, Map<String, Object>> orders = new LinkedHashMap<>(); // keeps the newest-first order
        try (Connection con = Db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long orderId = rs.getLong("order_id");
                    Map<String, Object> order = orders.get(orderId);
                    if (order == null) {
                        Timestamp when = rs.getTimestamp("order_date");
                        order = new LinkedHashMap<>();
                        order.put("orderId", orderId);
                        order.put("date", when.toLocalDateTime().format(DATE_FORMAT));
                        order.put("status", rs.getString("status"));
                        order.put("address", rs.getString("delivery_address"));
                        order.put("total", rs.getBigDecimal("total_amount"));
                        order.put("items", new ArrayList<Object>());
                        orders.put(orderId, order);
                    }
                    BigDecimal unitPrice = rs.getBigDecimal("unit_price");
                    long quantity = rs.getLong("quantity");

                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("name", rs.getString("name"));
                    line.put("quantity", quantity);
                    line.put("unitPrice", unitPrice);
                    line.put("lineTotal", unitPrice.multiply(BigDecimal.valueOf(quantity)));
                    @SuppressWarnings("unchecked")
                    List<Object> lines = (List<Object>) order.get("items");
                    lines.add(line);
                }
            }
        }
        return new ArrayList<Object>(orders.values());
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static void checkPassword(String password) {
        if (password.length() < 8 || password.length() > 64) {
            bad("Password must be 8 to 64 characters long.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            bad("Password must contain at least one letter and one number.");
        }
    }

    /** Throws a 400 error with a message the user can understand. */
    private static void bad(String message) {
        throw new ApiException(400, message);
    }

    /** A trimmed string from the JSON body ("" if missing or not a string). */
    private static String text(Map<String, Object> body, String key) {
        return rawText(body, key).trim();
    }

    private static String rawText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return (value instanceof String) ? (String) value : "";
    }

    /** Converts a JSON number to a long, or returns null if it is not a whole number. */
    private static Long wholeNumber(Object value) {
        if (!(value instanceof Double)) return null;
        double d = (Double) value;
        if (d != Math.floor(d) || Math.abs(d) > 1e9) return null;
        return (long) d;
    }

    private static Map<String, Object> message(String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", text);
        return map;
    }
}
