"use strict";

// QuickBite frontend. Plain JavaScript, no libraries.
// Every piece of data (menu, login, orders) comes from the Java backend, which reads Oracle.

// Served by the Java backend (the normal way): call "/api/..." on the same address.
// Opened as a file, or from "python3 -m http.server 5500": the backend is expected on localhost:8080.
const API_BASE = (location.protocol === "file:" || location.port === "5500") ? "http://localhost:8080" : "";
const MAX_QTY = 20;
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const STATUS_LABELS = {
  PLACED: "Placed",
  PREPARING: "Preparing",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled"
};

const $ = (selector) => document.querySelector(selector);

// ---------------------------------------------------------------------------
// Saved state (browser localStorage). The cart keeps only item ids and quantities.
// Prices are never stored or sent by the browser: the server reads them from Oracle.
// ---------------------------------------------------------------------------

function readStored(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch (e) {
    return fallback;
  }
}

function writeStored(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* storage unavailable */ }
}

function removeStored(key) {
  try { localStorage.removeItem(key); } catch (e) { /* storage unavailable */ }
}

function cleanCart(raw) {
  const result = {};
  if (raw && typeof raw === "object") {
    for (const [id, qty] of Object.entries(raw)) {
      const n = Number(qty);
      if (/^\d+$/.test(id) && Number.isInteger(n) && n >= 1) result[id] = Math.min(n, MAX_QTY);
    }
  }
  return result;
}

let menu = [];                                   // food items from GET /api/menu
let cart = cleanCart(readStored("cart", {}));    // { "itemId": quantity }
let token = readStored("token", null);           // login token from POST /api/login
let customer = readStored("customer", null);
let activeCategory = "All";

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

// Escape text before putting it into HTML, so names and addresses can never inject markup.
function esc(value) {
  const map = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  return String(value == null ? "" : value).replace(/[&<>"']/g, (c) => map[c]);
}

const rupee = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
function money(amount) { return rupee.format(Number(amount)); }

let toastTimer;
function toast(message) {
  const el = $("#toast");
  el.textContent = message;
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 3500);
}

// Calls the Java backend. Returns the JSON, or throws an Error with a readable message.
async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = "Bearer " + token;

  let response;
  try {
    response = await fetch(API_BASE + "/api" + path, Object.assign({}, options, { headers }));
  } catch (e) {
    throw new Error("Cannot reach the server. Check that the Java backend is running.");
  }

  let data = null;
  try { data = await response.json(); } catch (e) { /* the reply had no JSON body */ }

  if (!response.ok) {
    const isAuthCall = path === "/login" || path === "/register";
    if (response.status === 401 && token && !isAuthCall) {
      endSession();
      toast("Your session ended. Please log in again.");
      if (!$("#view-orders").hidden) showView("auth");
    }
    throw new Error((data && data.error) || "Something went wrong (error " + response.status + ").");
  }
  return data;
}

// ---------------------------------------------------------------------------
// Login session
// ---------------------------------------------------------------------------

function startSession(data) {
  token = data.token;
  customer = data.customer;
  writeStored("token", token);
  writeStored("customer", customer);
  renderNav();
}

function endSession() {
  token = null;
  customer = null;
  removeStored("token");
  removeStored("customer");
  $("#cart-address").value = "";
  renderNav();
}

function firstName() {
  return customer && customer.name ? customer.name.split(" ")[0] : "";
}

function renderNav() {
  const loggedIn = Boolean(token);
  $("#nav-login").hidden = loggedIn;
  $("#nav-logout").hidden = !loggedIn;
  $("#user-chip").hidden = !loggedIn;
  $("#user-chip").textContent = loggedIn ? "Hi, " + firstName() : "";
  $("#cart-count").textContent = Object.values(cart).reduce((sum, q) => sum + q, 0);
}

// ---------------------------------------------------------------------------
// Views: menu, auth (log in / create account), orders
// ---------------------------------------------------------------------------

function showView(name) {
  if (name === "orders" && !token) {
    showAuth("login", "Log in to see your orders.");
    return;
  }
  for (const view of ["menu", "auth", "orders"]) {
    $("#view-" + view).hidden = view !== name;
  }
  $("#nav-menu").classList.toggle("active", name === "menu");
  $("#nav-orders").classList.toggle("active", name === "orders");
  $("#nav-login").classList.toggle("active", name === "auth");
  window.scrollTo(0, 0);
  if (name === "orders") loadOrders();
}

function showAuth(tab, note) {
  setAuthTab(tab);
  $("#auth-note").hidden = !note;
  $("#auth-note").textContent = note || "";
  showView("auth");
}

function setAuthTab(tab) {
  const isLogin = tab === "login";
  $("#form-login").hidden = !isLogin;
  $("#form-register").hidden = isLogin;
  $("#tab-login").classList.toggle("active", isLogin);
  $("#tab-register").classList.toggle("active", !isLogin);
  $("#tab-login").setAttribute("aria-selected", String(isLogin));
  $("#tab-register").setAttribute("aria-selected", String(!isLogin));
  $("#login-error").textContent = "";
  $("#register-error").textContent = "";
}

// ---------------------------------------------------------------------------
// Menu
// ---------------------------------------------------------------------------

async function loadMenu() {
  const status = $("#menu-status");
  status.textContent = "Loading the menu...";
  try {
    menu = await api("/menu");
    status.textContent = menu.length
      ? ""
      : "The menu is empty. Run backend/sql/sample_data.sql in Oracle to add food items.";
    pruneCart();
  } catch (err) {
    menu = [];
    status.innerHTML = esc(err.message) + ' <button type="button" class="link-btn" id="retry-menu">Try again</button>';
  }
  renderChips();
  renderMenu();
  renderCart();
}

function renderChips() {
  const categories = ["All"].concat([...new Set(menu.map((item) => item.category))]);
  if (!categories.includes(activeCategory)) activeCategory = "All";
  $("#chips").innerHTML = menu.length
    ? categories.map((c) =>
        `<button type="button" class="chip${c === activeCategory ? " active" : ""}"` +
        ` data-category="${esc(c)}" aria-pressed="${c === activeCategory}">${esc(c)}</button>`).join("")
    : "";
}

function renderMenu() {
  const term = $("#search").value.trim().toLowerCase();
  const shown = menu.filter((item) =>
    (activeCategory === "All" || item.category === activeCategory) &&
    (!term || (item.name + " " + (item.description || "")).toLowerCase().includes(term)));

  const list = $("#menu-list");
  if (shown.length === 0) {
    list.innerHTML = menu.length
      ? '<p class="empty">No dishes match your search. Try another word or choose All.</p>'
      : "";
    return;
  }

  const groups = {};
  shown.forEach((item) => { (groups[item.category] = groups[item.category] || []).push(item); });

  list.innerHTML = Object.keys(groups).map((category) =>
    `<section class="menu-section"><h2>${esc(category)}</h2>${groups[category].map(dishHtml).join("")}</section>`
  ).join("");
}

function dishHtml(item) {
  return `<div class="dish">
    <div>
      <div class="dish-line">
        <h3>${esc(item.name)}</h3><span class="leader" aria-hidden="true"></span>
        <span class="price">${money(item.price)}</span>
      </div>
      <p class="dish-desc">${esc(item.description)}<span class="in-cart" data-incart="${item.id}">${inCartText(item.id)}</span></p>
    </div>
    <button type="button" class="btn" data-add="${item.id}" aria-label="Add ${esc(item.name)} to cart">Add</button>
  </div>`;
}

function inCartText(id) {
  return cart[id] ? cart[id] + " in cart" : "";
}

function refreshInCartLabels() {
  document.querySelectorAll("[data-incart]").forEach((el) => {
    el.textContent = inCartText(el.dataset.incart);
  });
}

// ---------------------------------------------------------------------------
// Cart
// ---------------------------------------------------------------------------

function saveCart() {
  writeStored("cart", cart);
  renderNav();
  refreshInCartLabels();
  renderCart();
}

// Remove cart entries for items that are no longer on the menu.
function pruneCart() {
  const ids = new Set(menu.map((item) => String(item.id)));
  for (const id of Object.keys(cart)) {
    if (!ids.has(id)) delete cart[id];
  }
  writeStored("cart", cart);
  renderNav();
}

function addToCart(id) {
  const item = menu.find((m) => String(m.id) === String(id));
  if (!item) return;
  if ((cart[id] || 0) >= MAX_QTY) {
    toast("You can order at most " + MAX_QTY + " of one item.");
    return;
  }
  cart[id] = (cart[id] || 0) + 1;
  saveCart();
  toast(item.name + " added to your cart.");
}

function changeQuantity(id, change) {
  const next = (cart[id] || 0) + change;
  if (next < 1) {
    delete cart[id];
  } else if (next > MAX_QTY) {
    toast("You can order at most " + MAX_QTY + " of one item.");
    return;
  } else {
    cart[id] = next;
  }
  saveCart();
}

function removeFromCart(id) {
  delete cart[id];
  saveCart();
}

function renderCart() {
  const byId = {};
  menu.forEach((item) => { byId[item.id] = item; });
  const ids = Object.keys(cart).filter((id) => byId[id]);

  // Remember which cart button had focus so keyboard users keep their place.
  const active = document.activeElement;
  const focusKey = active && active.closest("#cart-items") && active.dataset.act
    ? active.dataset.act + ":" + active.dataset.id : null;

  let totalPaise = 0; // whole paise avoid rounding errors; this total is only for display
  const rows = ids.map((id) => {
    const item = byId[id];
    const quantity = cart[id];
    const linePaise = Math.round(Number(item.price) * 100) * quantity;
    totalPaise += linePaise;
    return `<div class="cart-row">
      <div class="cart-name">${esc(item.name)}<small>${money(item.price)} each</small></div>
      <div class="cart-line">${money(linePaise / 100)}</div>
      <div class="stepper">
        <button type="button" data-act="dec" data-id="${id}" aria-label="Decrease quantity of ${esc(item.name)}">&minus;</button>
        <span>${quantity}</span>
        <button type="button" data-act="inc" data-id="${id}" aria-label="Increase quantity of ${esc(item.name)}">+</button>
      </div>
      <button type="button" class="link-btn" data-act="remove" data-id="${id}" aria-label="Remove ${esc(item.name)} from cart">Remove</button>
    </div>`;
  });

  $("#cart-items").innerHTML = rows.length
    ? rows.join("")
    : '<p class="empty">Your cart is empty. Add a dish from the menu.</p>';
  $("#cart-total").textContent = money(totalPaise / 100);
  $("#place-order").disabled = rows.length === 0;

  if (focusKey) {
    const [act, id] = focusKey.split(":");
    const again = $(`#cart-items [data-act="${act}"][data-id="${id}"]`);
    if (again) again.focus();
  }
}

function openCart() {
  renderCart();
  const addressBox = $("#cart-address");
  if (!addressBox.value.trim() && customer && customer.address) addressBox.value = customer.address;
  $("#cart-error").textContent = "";
  $("#cart").classList.add("open");
  $("#cart").setAttribute("aria-hidden", "false");
  $("#overlay").hidden = false;
  $("#cart-close").focus();
}

function closeCart() {
  $("#cart").classList.remove("open");
  $("#cart").setAttribute("aria-hidden", "true");
  $("#overlay").hidden = true;
}

async function placeOrder() {
  const error = $("#cart-error");
  error.textContent = "";

  if (!token) {
    closeCart();
    showAuth("login", "Log in or create an account to place your order. Your cart is saved.");
    return;
  }
  const address = $("#cart-address").value.trim();
  if (address.length < 5) {
    error.textContent = "Enter your delivery address (at least 5 characters).";
    $("#cart-address").focus();
    return;
  }
  // Only ids and quantities are sent. The server looks up the prices in Oracle.
  const items = Object.keys(cart).map((id) => ({ itemId: Number(id), quantity: cart[id] }));
  if (items.length === 0) {
    error.textContent = "Your cart is empty.";
    return;
  }

  const button = $("#place-order");
  button.disabled = true;
  button.textContent = "Placing order...";
  try {
    const result = await api("/orders", { method: "POST", body: JSON.stringify({ address, items }) });
    cart = {};
    saveCart();
    closeCart();
    toast("Order #" + result.orderId + " placed. Total " + money(result.total) + ".");
    showView("orders");
  } catch (err) {
    error.textContent = err.message;
  } finally {
    button.textContent = "Place order";
    renderCart();
  }
}

// ---------------------------------------------------------------------------
// Order history
// ---------------------------------------------------------------------------

async function loadOrders() {
  const status = $("#orders-status");
  const list = $("#orders-list");
  list.innerHTML = "";
  status.textContent = "Loading your orders...";
  try {
    const orders = await api("/orders");
    if (orders.length === 0) {
      status.textContent = "You have not placed any orders yet. Pick a dish from the menu to start.";
      return;
    }
    status.textContent = "";
    list.innerHTML = orders.map(orderHtml).join("");
  } catch (err) {
    status.textContent = err.message;
  }
}

function orderHtml(order) {
  const lines = order.items.map((line) =>
    `<li><span>${esc(line.name)} &times; ${line.quantity}</span><span>${money(line.lineTotal)}</span></li>`).join("");
  const label = STATUS_LABELS[order.status] || order.status;
  return `<article class="ticket">
    <div class="ticket-head">
      <div><strong>Order #${order.orderId}</strong><small>${esc(order.date)}</small></div>
      <span class="badge status-${esc(order.status)}">${esc(label)}</span>
    </div>
    <ul>${lines}</ul>
    <div class="ticket-foot">
      <span class="muted">Deliver to: ${esc(order.address)}</span>
      <strong>${money(order.total)}</strong>
    </div>
  </article>`;
}

// ---------------------------------------------------------------------------
// Forms: log in and create account
// ---------------------------------------------------------------------------

function showFormError(errorEl, inputId, message) {
  errorEl.textContent = message;
  $("#" + inputId).focus();
}

function afterLogin() {
  toast("Welcome, " + firstName() + ".");
  showView("menu");
  if (Object.keys(cart).length > 0) openCart();
}

$("#form-login").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = $("#login-error");
  error.textContent = "";
  const email = $("#login-email").value.trim();
  const password = $("#login-password").value;

  if (!EMAIL_RE.test(email)) return showFormError(error, "login-email", "Enter a valid email address.");
  if (!password) return showFormError(error, "login-password", "Enter your password.");

  const button = $("#login-submit");
  button.disabled = true;
  try {
    startSession(await api("/login", { method: "POST", body: JSON.stringify({ email, password }) }));
    event.target.reset();
    afterLogin();
  } catch (err) {
    error.textContent = err.message;
  } finally {
    button.disabled = false;
  }
});

$("#form-register").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = $("#register-error");
  error.textContent = "";
  const values = {
    fullName: $("#reg-name").value.trim(),
    email: $("#reg-email").value.trim(),
    phone: $("#reg-phone").value.trim(),
    address: $("#reg-address").value.trim(),
    password: $("#reg-password").value
  };
  const confirm = $("#reg-confirm").value;

  if (values.fullName.length < 2) return showFormError(error, "reg-name", "Enter your full name (at least 2 characters).");
  if (!EMAIL_RE.test(values.email)) return showFormError(error, "reg-email", "Enter a valid email address.");
  if (!/^[0-9]{10}$/.test(values.phone)) return showFormError(error, "reg-phone", "Phone number must be exactly 10 digits.");
  if (values.password.length < 8 || !/[A-Za-z]/.test(values.password) || !/[0-9]/.test(values.password)) {
    return showFormError(error, "reg-password", "Password must be at least 8 characters and include a letter and a number.");
  }
  if (values.password !== confirm) return showFormError(error, "reg-confirm", "The two passwords do not match.");

  const button = $("#register-submit");
  button.disabled = true;
  try {
    await api("/register", { method: "POST", body: JSON.stringify(values) });
    // Account created: log in straight away with the same details.
    startSession(await api("/login", {
      method: "POST",
      body: JSON.stringify({ email: values.email, password: values.password })
    }));
    event.target.reset();
    afterLogin();
  } catch (err) {
    error.textContent = err.message;
  } finally {
    button.disabled = false;
  }
});

// ---------------------------------------------------------------------------
// Wiring up the page
// ---------------------------------------------------------------------------

$("#brand").addEventListener("click", () => showView("menu"));
$("#nav-menu").addEventListener("click", () => showView("menu"));
$("#nav-orders").addEventListener("click", () => showView("orders"));
$("#nav-login").addEventListener("click", () => showAuth("login"));
$("#tab-login").addEventListener("click", () => setAuthTab("login"));
$("#tab-register").addEventListener("click", () => setAuthTab("register"));

$("#nav-logout").addEventListener("click", async () => {
  try { await api("/logout", { method: "POST" }); } catch (e) { /* log out locally anyway */ }
  endSession();
  showView("menu");
  toast("You have been logged out.");
});

$("#search").addEventListener("input", renderMenu);

$("#chips").addEventListener("click", (event) => {
  const chip = event.target.closest("[data-category]");
  if (!chip) return;
  activeCategory = chip.dataset.category;
  renderChips();
  renderMenu();
});

$("#menu-list").addEventListener("click", (event) => {
  const button = event.target.closest("[data-add]");
  if (button) addToCart(button.dataset.add);
});

$("#cart-items").addEventListener("click", (event) => {
  const button = event.target.closest("[data-act]");
  if (!button) return;
  const id = button.dataset.id;
  if (button.dataset.act === "inc") changeQuantity(id, 1);
  if (button.dataset.act === "dec") changeQuantity(id, -1);
  if (button.dataset.act === "remove") removeFromCart(id);
});

$("#cart-toggle").addEventListener("click", openCart);
$("#cart-close").addEventListener("click", () => { closeCart(); $("#cart-toggle").focus(); });
$("#overlay").addEventListener("click", closeCart);
$("#place-order").addEventListener("click", placeOrder);

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && $("#cart").classList.contains("open")) {
    closeCart();
    $("#cart-toggle").focus();
  }
});

document.addEventListener("click", (event) => {
  if (event.target.id === "retry-menu") loadMenu();
});

renderNav();
showView("menu");
loadMenu();
