
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class Main {

    private static final int MAX_BODY_BYTES = 100_000;
    private static Path frontendDir;

    public static void main(String[] args) throws Exception {
        frontendDir = findFrontendDir();

        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "8080"));

        checkDatabase();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/api/", Main::handleApi);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("QuickBite server running on port " + port);
        System.out.println("Frontend folder: " + frontendDir);

        if (!Files.isRegularFile(frontendDir.resolve("index.html"))) {
            System.out.println(
                    "WARNING: index.html was not found in the frontend folder.");
        }
    }

    private static Path findFrontendDir() {
        String fromEnv = System.getenv("FRONTEND_DIR");

        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv).toAbsolutePath().normalize();
        }

        Path inParent = Paths.get("../frontend")
                .toAbsolutePath().normalize();

        Path inHere = Paths.get("frontend")
                .toAbsolutePath().normalize();

        if (!Files.isRegularFile(inParent.resolve("index.html"))
                && Files.isRegularFile(inHere.resolve("index.html"))) {
            return inHere;
        }

        return inParent;
    }

    // Check PostgreSQL connectivity.
    private static void checkDatabase() {
        System.out.println("Connecting to PostgreSQL...");

        try (Connection con = Db.getConnection()) {
            System.out.println("PostgreSQL connection successful.");
            System.out.println(
                    "Database version: "
                    + con.getMetaData().getDatabaseProductVersion());

        } catch (SQLException e) {
            // Log the error but allow the server to start.
            // API requests will return an error until the DB is available.
            System.err.println(
                    "WARNING: PostgreSQL connection failed: "
                    + e.getMessage());

            System.err.println(
                    "Check DATABASE_URL, DATABASE_USER, "
                    + "and DATABASE_PASSWORD.");
        }
    }

    // ---------------------------------------------------------
    // API ROUTES
    // ---------------------------------------------------------

    private static void handleApi(HttpExchange ex) throws IOException {

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*");

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization");

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS");

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        int status = 200;
        Object result;

        try {
            String route = ex.getRequestMethod()
                    + " " + ex.getRequestURI().getPath();

            switch (route) {

                case "POST /api/register":
                    result = Api.register(readBody(ex));
                    status = 201;
                    break;

                case "POST /api/login":
                    result = Api.login(readBody(ex));
                    break;

                case "POST /api/logout":
                    Security.endSession(bearerToken(ex));
                    result = Collections.singletonMap(
                            "message", "Logged out.");
                    break;

                case "GET /api/menu":
                    result = Api.menu();
                    break;

                case "GET /api/orders":
                    result = Api.orderHistory(requireLogin(ex));
                    break;

                case "POST /api/orders":
                    result = Api.placeOrder(
                            requireLogin(ex), readBody(ex));
                    status = 201;
                    break;

                default:
                    throw new Api.ApiException(
                            404, "Unknown API endpoint.");
            }

        } catch (Api.ApiException e) {
            status = e.status;
            result = Collections.singletonMap(
                    "error", e.getMessage());

        } catch (SQLException e) {
            e.printStackTrace();

            status = 503;
            result = Collections.singletonMap(
                    "error",
                    "The database is unavailable. Please try again later.");

        } catch (Exception e) {
            e.printStackTrace();

            status = 500;
            result = Collections.singletonMap(
                    "error", "Something went wrong on the server.");
        }

        byte[] bytes = Json.toJson(result)
                .getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8");

        ex.getResponseHeaders().set(
                "Cache-Control", "no-store");

        send(ex, status, bytes);
    }

    // ---------------------------------------------------------
    // READ JSON REQUEST BODY
    // ---------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(
            HttpExchange ex) throws IOException {

        byte[] raw = ex.getRequestBody()
                .readNBytes(MAX_BODY_BYTES + 1);

        if (raw.length > MAX_BODY_BYTES) {
            throw new Api.ApiException(
                    413, "The request is too large.");
        }

        Object parsed;

        try {
            parsed = Json.parse(
                    new String(raw, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new Api.ApiException(
                    400, "The request is not valid JSON.");
        }

        if (!(parsed instanceof Map)) {
            throw new Api.ApiException(
                    400, "The request body must be a JSON object.");
        }

        return (Map<String, Object>) parsed;
    }

    // ---------------------------------------------------------
    // AUTHENTICATION
    // ---------------------------------------------------------

    private static String bearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders()
                .getFirst("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }

        return null;
    }

    private static long requireLogin(HttpExchange ex) {
        Long customerId =
                Security.customerIdFor(bearerToken(ex));

        if (customerId == null) {
            throw new Api.ApiException(
                    401, "Please log in to continue.");
        }

        return customerId;
    }

    // ---------------------------------------------------------
    // SERVE FRONTEND FILES
    // ---------------------------------------------------------

    private static void handleStatic(HttpExchange ex)
            throws IOException {

        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendText(ex, 405, "Method not allowed");
                return;
            }

            String path = ex.getRequestURI().getPath();

            if (path.equals("/")) {
                path = "/index.html";
            }

            Path file = frontendDir
                    .resolve(path.substring(1))
                    .normalize();

            // Prevent access to files outside the frontend directory.
            if (!file.startsWith(frontendDir)
                    || !Files.isRegularFile(file)) {
                sendText(ex, 404, "Not found");
                return;
            }

            ex.getResponseHeaders().set(
                    "Content-Type", contentType(file.toString()));

            ex.getResponseHeaders().set(
                    "Cache-Control", "no-cache");

            send(ex, 200, Files.readAllBytes(file));

        } catch (java.nio.file.InvalidPathException e) {
            sendText(ex, 404, "Not found");
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".ico")) {
            return "image/x-icon";
        }

        return "application/octet-stream";
    }

    private static void sendText(
            HttpExchange ex, int status, String text)
            throws IOException {

        ex.getResponseHeaders().set(
                "Content-Type", "text/plain; charset=utf-8");

        send(ex, status, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(
            HttpExchange ex, int status, byte[] bytes)
            throws IOException {

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
