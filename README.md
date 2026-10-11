# QuickBite - Online Food Delivery (HTML/CSS/JS + Java JDBC + Oracle)

A small full-stack web application for a college practical (PAT 4).

| Layer | Technology |
|-------|------------|
| Frontend | HTML, CSS, vanilla JavaScript (no libraries) |
| Backend | Java (JDK built-in `HttpServer`) with plain JDBC |
| Database | Oracle SQL (Oracle Database XE / Free, or any Oracle 12c+) |
| Communication | HTTP requests with JSON |

**Features:** customer registration and login, menu loaded from Oracle, cart (add, change quantity, remove, total), place an order (saved in Oracle), order history, input validation with clear messages.

---

## 1. Files and their purposes

```
food-delivery-app/
├── README.md                    This guide
├── .gitignore                   Keeps compiled files, the JDBC jar and passwords out of Git
├── frontend/
│   ├── index.html               The page: menu, log in / create account, orders, cart drawer
│   ├── style.css                All styling (responsive)
│   └── app.js                   Browser logic: calls the backend with fetch() and draws the results
└── backend/
    ├── src/
    │   ├── Main.java            Starts the server, routes /api/... requests, serves the frontend files
    │   ├── Api.java             The logic of each endpoint, with all the JDBC code and SQL
    │   ├── Db.java              Opens the Oracle connection (settings come from environment variables)
    │   ├── Security.java        Password hashing (PBKDF2) and login tokens
    │   └── Json.java            Tiny JSON reader/writer (so no external library is needed)
    └── sql/
        ├── schema.sql           DDL: sequences, tables, keys, constraints, indexes
        ├── sample_data.sql      DML: INSERT menu items, one demo customer and one demo order
        └── queries.sql          Practice SELECT, UPDATE and DELETE statements
```

Two folders are created on your computer while you work and are ignored by Git: `backend/out/` (compiled classes) and `backend/lib/` (the Oracle JDBC driver).

---

## 2. How it works (for your viva)

```
Browser (index.html + app.js)
   |  fetch("/api/...") with JSON
   v
Java server (Main.java)  ->  Api.java  ->  JDBC (PreparedStatement)  ->  Oracle
```

| Endpoint | Login needed | What it does |
|----------|--------------|--------------|
| `POST /api/register` | no | Validates the input, hashes the password, `INSERT`s into `customers` |
| `POST /api/login` | no | Finds the customer by email, checks the password hash, returns a random token |
| `POST /api/logout` | token | Ends the login session |
| `GET /api/menu` | no | `SELECT`s available rows from `food_items` |
| `POST /api/orders` | yes | Reads prices from Oracle, calculates the total, inserts into `orders` and `order_items` in one transaction |
| `GET /api/orders` | yes | `SELECT`s the order history of the logged-in customer only (4-table join) |

**Security and correctness points**

- **Prices are never trusted from the browser.** The cart sends only `itemId` and `quantity`. `Api.placeOrder` reads each price from `food_items` and calculates the total itself. Any `price` field a browser adds is ignored.
- **Passwords are never stored.** Only a salted PBKDF2-HMAC-SHA256 hash is saved (`120000:salt:hash`).
- **Prepared statements** (`?` placeholders) are used for every query, which prevents SQL injection.
- **Database credentials are not in the code.** They are read from the environment variables `ORACLE_USER` and `ORACLE_PASSWORD`. The browser never sees them.
- **Transaction:** the order and all its lines are saved together. If anything fails, `rollback()` undoes everything.
- **Database rules:** primary keys, foreign keys, `UNIQUE` email, `CHECK` constraints (price > 0, quantity 1 to 20, valid status), `ON DELETE CASCADE` from orders to order items.
- **Output escaping:** the frontend escapes text from the database before showing it, which prevents cross-site scripting.

Login tokens are kept in the server's memory, so restarting the Java server logs everyone out. That is fine for this project.

---

## 3. Prerequisites (macOS)

1. **Java JDK 17 or newer** (JDK 21 recommended). Check with:
   ```bash
   java -version
   javac -version
   ```
   If missing: `brew install --cask temurin@21` (or download Temurin from https://adoptium.net), then open a new Terminal window.
2. **An Oracle Database 12c or newer** that you can connect to. Pick one:
   - **Option A - your college/lab Oracle server.** Ask for the host, port, service name (or SID), user name and password. Skip to section 4, step 2.
   - **Option B - Oracle Database Free in Docker on your Mac** (steps below). Works on Intel and Apple Silicon Macs.
3. **The Oracle JDBC driver** (a `.jar` file). Section 5 shows how to download it.

### Option B: Oracle Database Free in Docker

Install Docker Desktop from https://www.docker.com/products/docker-desktop (or `brew install --cask docker`), open it once, and wait until it says it is running. Then:

```bash
docker run -d --name oracle-free -p 1521:1521 \
  -e ORACLE_PASSWORD=SystemPass123 \
  -e APP_USER=fooduser \
  -e APP_USER_PASSWORD=FoodPass123 \
  -v oracle-food-data:/opt/oracle/oradata \
  gvenzl/oracle-free:slim
```

The first start takes a few minutes. Watch the log until you see `DATABASE IS READY TO USE!`, then press Ctrl+C (the container keeps running):

```bash
docker logs -f oracle-free
```

This creates a user `fooduser` (password `FoodPass123`) in the pluggable database `FREEPDB1`. Later, start and stop it with `docker start oracle-free` and `docker stop oracle-free`; your data is kept in the `oracle-food-data` volume.

If running `schema.sql` later fails with `ORA-01031: insufficient privileges` or `ORA-01950: no privileges on tablespace`, give the user the rights it needs:

```bash
docker exec -i oracle-free sqlplus -s system/SystemPass123@//localhost:1521/FREEPDB1 <<'EOF'
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO fooduser;
ALTER USER fooduser QUOTA UNLIMITED ON USERS;
EOF
```

### Using a different Oracle installation (Option A, or XE)

Create the application user as an administrator (for example `SYSTEM`), in the pluggable database (`XEPDB1` for Oracle XE, `FREEPDB1` for Oracle Free):

```sql
CREATE USER fooduser IDENTIFIED BY FoodPass123;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO fooduser;
ALTER USER fooduser QUOTA UNLIMITED ON USERS;
```

Your JDBC URL then uses your own host, port and service name:

| Setup | `ORACLE_URL` |
|-------|--------------|
| Oracle Free (Docker above) | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` (this is the default) |
| Oracle XE | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |
| College server | `jdbc:oracle:thin:@//HOST:1521/SERVICE_NAME` |

---

## 4. Run the SQL scripts

Run `schema.sql` first, then `sample_data.sql`. Both must run as the application user (`fooduser`).

1. **Create the tables** (DDL):
   ```bash
   cd food-delivery-app
   docker exec -i oracle-free sqlplus -s fooduser/FoodPass123@//localhost:1521/FREEPDB1 < backend/sql/schema.sql
   ```
2. **Insert the sample data** (DML):
   ```bash
   docker exec -i oracle-free sqlplus -s fooduser/FoodPass123@//localhost:1521/FREEPDB1 < backend/sql/sample_data.sql
   ```
   (Replace `fooduser/FoodPass123` with your own user and password if you use another setup.)

   No error messages mean success. The sample data adds 16 menu items, one demo customer and one demo order.

**Not using Docker?** Open the `.sql` files in **Oracle SQL Developer** (free from oracle.com), connect as your user, open the file and press **F5** (Run Script). Run `schema.sql`, then `sample_data.sql`. SQL*Plus and SQLcl also work: `@backend/sql/schema.sql`.

3. **Check it worked:**
   ```bash
   docker exec -i oracle-free sqlplus -s fooduser/FoodPass123@//localhost:1521/FREEPDB1 <<'EOF'
   SELECT COUNT(*) AS menu_items FROM food_items;
   SELECT customer_id, full_name, email FROM customers;
   EOF
   ```
   You should see 16 menu items and the customer `demo@example.com`.

`backend/sql/queries.sql` contains practice `SELECT`, `UPDATE` and `DELETE` statements. Run them one at a time; the data-changing ones are followed by `ROLLBACK`.

To wipe everything and start again, run the `DROP` lines that are commented out at the top of `schema.sql`, then run both scripts again.

**Demo account:** `demo@example.com` / `Demo@1234`. You can also create your own account on the website.

---

## 5. Download the Oracle JDBC driver

From the project folder:

```bash
mkdir -p backend/lib
curl -L -o backend/lib/ojdbc11.jar \
  https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar
ls -lh backend/lib/ojdbc11.jar
```

The file should be a few megabytes. If the download says "404", open https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/ in a browser, choose a version folder, and download its `ojdbc11-<version>.jar` into `backend/lib/ojdbc11.jar`. (`ojdbc11` works with JDK 11 and newer.)

The driver is only needed when the program runs. The code itself uses only the standard `java.sql` classes. The `.gitignore` keeps the jar out of Git.

---

## 6. Compile and start the backend

```bash
cd backend
mkdir -p out
javac -d out src/*.java

export ORACLE_URL="jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
export ORACLE_USER="fooduser"
export ORACLE_PASSWORD="FoodPass123"

java -cp "out:lib/*" Main
```

Expected output:

```
Connecting to Oracle at jdbc:oracle:thin:@//localhost:1521/FREEPDB1 ...
Oracle connection OK: 23.x.x.x.x
Server running at http://localhost:8080
Frontend folder: /.../food-delivery-app/frontend
```

If you see `WARNING: cannot connect to Oracle`, read the message and use the troubleshooting table in section 9. Leave this Terminal window open while you use the app. Press **Ctrl+C** to stop the server.

The `export` lines only last for that Terminal window. Run them again in every new window. Optional: `PORT=9090` changes the web port.

## 7. Open the frontend

The Java server also serves the `frontend/` folder, so there is nothing else to start. Open:

**http://localhost:8080**

Optional, to serve the frontend separately (the backend must still be running on port 8080):

```bash
cd frontend
python3 -m http.server 5500
```

Then open http://localhost:5500. `app.js` detects this and sends its requests to `http://localhost:8080`.

---

## 8. Test cases

Do these in order after the app starts. Every row should behave as described.

### Using the website

| # | What to do | Expected result |
|---|-----------|-----------------|
| 1 | Open http://localhost:8080 | The menu appears with 16 dishes in categories and prices in rupees |
| 2 | In Oracle run `UPDATE food_items SET price = 260 WHERE name = 'Margherita Pizza'; COMMIT;` and refresh the page | The pizza now shows Rs 260.00. This proves prices come from Oracle |
| 3 | Click **Create account**, press the button with empty fields | A message asks for your full name |
| 4 | Try a bad email, a phone with 9 digits, a password `abc`, and two different passwords | A clear message for each, nothing is sent to the server |
| 5 | Register with valid details | You are logged in and see "Hi, <your first name>" in the top bar |
| 6 | Register again with the same email | "An account with this email already exists." |
| 7 | Log out, then log in with a wrong password | "Incorrect email or password." |
| 8 | Log in with the correct password | Logged in |
| 9 | Click **Add** on three dishes, add one dish twice | The cart badge shows 4 |
| 10 | Open the cart. Use **+** and **-**, then **Remove** | Quantities and the total update. At quantity 1, **-** removes the item |
| 11 | Log out, add a dish, open the cart and click **Place order** | You are sent to the log-in page and your cart is kept |
| 12 | Log in, open the cart, enter an address shorter than 5 characters, click **Place order** | "Enter your delivery address (at least 5 characters)." |
| 13 | Enter a proper address and click **Place order** | A message "Order #N placed" and the **My orders** page opens |
| 14 | Check the order page | The order with its items, total and status "Placed" |
| 15 | In Oracle run the check query below | New rows in `orders` and `order_items`, `unit_price` equal to the menu price |
| 16 | Register a second account and open **My orders** | An empty list: you only see your own orders |
| 17 | Log in as `demo@example.com` (`Demo@1234`) and open **My orders** | The demo order (2 x Margherita Pizza, 1 x Gulab Jamun) |
| 18 | Stop the backend (Ctrl+C) and reload the page | "Cannot reach the server..." with a **Try again** button |
| 19 | Stop Oracle (`docker stop oracle-free`) with the backend running, reload | "The database is not available right now..." |

Check query for test 15:

```bash
docker exec -i oracle-free sqlplus -s fooduser/FoodPass123@//localhost:1521/FREEPDB1 <<'EOF'
SET LINESIZE 200
SELECT o.order_id, c.email, o.total_amount, o.status, f.name, oi.quantity, oi.unit_price
FROM orders o
JOIN customers c   ON c.customer_id = o.customer_id
JOIN order_items oi ON oi.order_id = o.order_id
JOIN food_items f   ON f.item_id = oi.item_id
ORDER BY o.order_id DESC, oi.order_item_id;
EOF
```

### Using the API directly (Terminal, backend running)

```bash
# Log in and keep the token
TOKEN=$(curl -s -X POST localhost:8080/api/login \
  -d '{"email":"demo@example.com","password":"Demo@1234"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

# Price tampering: the browser claims price 0.01, the server must ignore it.
# Expect "total":249.00 (or the current menu price of item 1) - NOT 0.01
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" \
  -d '{"address":"Hostel B, Room 204","items":[{"itemId":1,"quantity":1,"price":0.01}]}'

# Not logged in: expect 401 {"error":"Please log in to continue."}
curl -s -i localhost:8080/api/orders

# Quantity too large: expect 400
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" \
  -d '{"address":"Hostel B, Room 204","items":[{"itemId":1,"quantity":99}]}'

# SQL injection attempt in the login form: expect 400 (invalid email) or 401, never a login
curl -s -X POST localhost:8080/api/login -d "{\"email\":\"' OR '1'='1\",\"password\":\"x\"}"
```

### What has been verified, and what has not

| Verified while building (no Oracle was available) | Needs you to run it with Oracle |
|----|----|
| Java code compiles without warnings; the server starts and serves the frontend files | Connecting to Oracle with the JDBC driver |
| Password hashing, wrong-password rejection, login tokens, JSON reading and writing (automated checks) | `schema.sql`, `sample_data.sql`, `queries.sql` executing without errors (the scripts were reviewed but never run on Oracle) |
| Registration, login and order input validation return the right 400/401 messages, and a valid request reaches the database layer | Registration, login, menu, order placing and order history reading and writing real rows |
| Without a database the API answers 503 with a friendly message and no stack trace in the browser | The transaction and rollback behaviour on a real database |
| The frontend (menu, search, category filter, cart, address checks, login/register forms, order history, logout) behaves correctly against a stand-in for the API, and the order request carries no prices | |

So the Oracle-dependent features are written and wired end to end, but you must run the test cases above on your machine to confirm them.

---

## 9. Troubleshooting

| Message or problem | Cause and fix |
|--------------------|---------------|
| `WARNING: cannot connect to Oracle: No suitable driver found for jdbc:oracle...` | The JDBC jar is not on the classpath. Check that `backend/lib/ojdbc11.jar` exists and that you run `java -cp "out:lib/*" Main` from the `backend` folder (keep the quotes) |
| `ORACLE_USER and ORACLE_PASSWORD environment variables are not set` | Run the three `export` lines in the same Terminal window before `java ...` |
| `ORA-01017: invalid username/password` | Wrong user or password. Make sure `ORACLE_USER` and `ORACLE_PASSWORD` match the user you created |
| `ORA-12541: TNS:no listener` or `Connection refused` | Oracle is not running or not on that port. For Docker: `docker ps`, then `docker start oracle-free` and wait for it to be ready |
| `ORA-12514` / `ORA-12505` (service or SID not known) | Wrong service name in `ORACLE_URL`. Use `FREEPDB1` (Free) or `XEPDB1` (XE). Do not use `/XE` unless your college gives it as the service name |
| `ORA-00942: table or view does not exist` (shown in the backend window) | `schema.sql` was not run, or it was run as a different user than `ORACLE_USER` |
| `ORA-00955: name is already used by an existing object` | `schema.sql` was already run. Use the commented `DROP` lines at its top to start again |
| `ORA-01950: no privileges on tablespace 'USERS'` or `ORA-01031` | Run the `GRANT` and `QUOTA` commands from section 3 as an administrator |
| `ORA-02291` / `ORA-02292` | A foreign key protects the data (for example, you cannot delete a dish that appears in an order). This is expected behaviour |
| Menu says "The menu is empty" | `sample_data.sql` was not run, or it was rolled back. Run it again (and make sure it ends with `COMMIT`) |
| `Address already in use` when starting | Port 8080 is busy. Stop the other program, or start with `PORT=9090 java -cp "out:lib/*" Main` and open http://localhost:9090 |
| `curl ... 404` when downloading the jar | Pick an existing version from the Maven folder URL in section 5 |
| Page loads but shows "Cannot reach the server" | The backend is not running, or you opened the page from another port than the backend. Open http://localhost:8080 |
| You were logged out suddenly | The backend was restarted (tokens live in memory) or 8 hours passed. Log in again |
| `docker: command not found` / "Cannot connect to the Docker daemon" | Install and open Docker Desktop and wait until it is running |
| Apple Silicon slow start | The first start of the Oracle container takes a few minutes. Wait for `DATABASE IS READY TO USE!` |

---

## 10. Quick command summary (macOS)

```bash
# one time
docker run -d --name oracle-free -p 1521:1521 -e ORACLE_PASSWORD=SystemPass123 \
  -e APP_USER=fooduser -e APP_USER_PASSWORD=FoodPass123 \
  -v oracle-food-data:/opt/oracle/oradata gvenzl/oracle-free:slim
docker logs -f oracle-free                      # wait for "DATABASE IS READY TO USE!", then Ctrl+C

cd food-delivery-app
docker exec -i oracle-free sqlplus -s fooduser/FoodPass123@//localhost:1521/FREEPDB1 < backend/sql/schema.sql
docker exec -i oracle-free sqlplus -s fooduser/FoodPass123@//localhost:1521/FREEPDB1 < backend/sql/sample_data.sql

mkdir -p backend/lib
curl -L -o backend/lib/ojdbc11.jar \
  https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar

# every time
cd backend
mkdir -p out && javac -d out src/*.java
export ORACLE_URL="jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
export ORACLE_USER="fooduser"
export ORACLE_PASSWORD="FoodPass123"
java -cp "out:lib/*" Main
# then open http://localhost:8080
```

## 11. Pushing to GitHub

```bash
cd food-delivery-app
git init
git add .
git status        # check: no .jar, no out/ folder, no passwords
git commit -m "Online food delivery app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```
