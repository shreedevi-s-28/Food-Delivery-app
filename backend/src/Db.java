import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection to Oracle.
 *
 * The settings come from environment variables so that no password is ever
 * written into the source code or pushed to GitHub:
 *
 *   ORACLE_URL       default: jdbc:oracle:thin:@//localhost:1521/FREEPDB1
 *   ORACLE_USER      required
 *   ORACLE_PASSWORD  required
 *
 * The Oracle JDBC driver (ojdbc jar) must be on the classpath at run time.
 * It registers itself automatically, so no Class.forName() call is needed.
 */
public class Db {

    private static final String URL = valueOrDefault(System.getenv("ORACLE_URL"),
            "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
    private static final String USER = System.getenv("ORACLE_USER");
    private static final String PASSWORD = System.getenv("ORACLE_PASSWORD");

    /** Opens a new connection. Callers close it with try-with-resources. */
    public static Connection connect() throws SQLException {
        if (USER == null || PASSWORD == null) {
            throw new SQLException("ORACLE_USER and ORACLE_PASSWORD environment variables are not set");
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static String url() {
        return URL;
    }

    private static String valueOrDefault(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }
}
