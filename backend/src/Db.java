import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DATABASE_USER");
        String password = System.getenv("DATABASE_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new SQLException("Database environment variables are not configured.");
        }

        return DriverManager.getConnection(url, user, password);
    }
}
