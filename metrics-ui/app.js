const BASE_URL = '/api/v1';
let token = localStorage.getItem('token') || null;
let isActionLoading = false;
let metricsChart;
let userRole = null;
let currentUser = null;

let metricsCurrentPage = 1;
let metricsTotalPages = 1;
const METRICS_PAGE_SIZE = 2;

let cardsCurrentPage = 1;
let cardsPageSize = 2;
let allLoadedCards = [];

let usersCurrentPage = 1;
let usersTotalPages = 1;
const USERS_PAGE_SIZE = 2;
let usersSearchQuery = '';

window.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    if (token) {
        parseTokenAndRole();
        showPage('dashboardPage');
        fetchMetrics(metricsCurrentPage);
    } else {
        showPage('loginPage');
    }
});

function setupEventListeners() {
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('btnValidate').addEventListener('click', () => triggerAction('validate', 'btnValidate', 'Trigger Validate'));
    document.getElementById('btnLoad').addEventListener('click', () => triggerAction('load-data', 'btnLoad', 'Trigger Load Data'));

    document.getElementById('btnBack').addEventListener('click', () => changeMetricsPage(-1));
    document.getElementById('btnNext').addEventListener('click', () => changeMetricsPage(1));

    const btnCardsBack = document.getElementById('btnCardsBack');
    const btnCardsNext = document.getElementById('btnCardsNext');
    if (btnCardsBack) btnCardsBack.addEventListener('click', () => changeCardsPage(-1));
    if (btnCardsNext) btnCardsNext.addEventListener('click', () => changeCardsPage(1));

    const btnUsersBack = document.getElementById('btnUsersBack');
    const btnUsersNext = document.getElementById('btnUsersNext');
    if (btnUsersBack) btnUsersBack.addEventListener('click', () => changeUsersPage(-1));
    if (btnUsersNext) btnUsersNext.addEventListener('click', () => changeUsersPage(1));

    const userSearchInput = document.getElementById('userSearchInput');
    if (userSearchInput) {
        let debounceTimer;
        userSearchInput.addEventListener('input', (e) => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                usersSearchQuery = e.target.value.trim();
                usersCurrentPage = 1;
                fetchUsers();
            }, 400);
        });
    }

    document.getElementById('btnLogout').addEventListener('click', handleLogout);
    document.getElementById('secureCardForm').addEventListener('submit', handleSaveCard);
    document.getElementById('createUserForm').addEventListener('submit', handleCreateUser);
}

function parseTokenAndRole() {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));

        const payload = JSON.parse(jsonPayload);
        userRole = payload.role;
        currentUser = payload.sub;

        cardsPageSize = (userRole === 'ROLE_ADMIN') ? 2 : 8;

        if (userRole === 'ROLE_ADMIN') {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.remove('hidden'));
        } else {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.add('hidden'));
        }
    } catch (e) {
        console.error("Failed to extract claims from JWT", e);
    }
}

function showPage(pageId) {
    document.getElementById('loginPage').classList.add('hidden');
    document.getElementById('dashboardPage').classList.add('hidden');
    document.getElementById(pageId).classList.remove('hidden');

    if (pageId === 'dashboardPage') {
        if (!metricsChart) initChart();
        switchTab('metricsTab');
    }
}

function switchTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.add('hidden'));
    document.querySelectorAll('.nav-tab').forEach(btn => btn.classList.remove('active'));

    document.getElementById(tabId).classList.remove('hidden');

    const activeBtn = Array.from(document.querySelectorAll('.nav-tab')).find(btn => {
        const onclickAttr = btn.getAttribute('onclick');
        return onclickAttr && onclickAttr.includes(tabId);
    });
    if (activeBtn) activeBtn.classList.add('active');

    if (tabId === 'cardsTab') {
        cardsCurrentPage = 1;
        fetchCards();
    }
    if (tabId === 'metricsTab') {
        fetchMetrics(metricsCurrentPage);
    }
    if (tabId === 'usersTab') {
        usersCurrentPage = 1;
        fetchUsers();
    }
}

function base64ToArrayBuffer(b64) {
    const binaryString = window.atob(b64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}

async function handleLogin(e) {
    e.preventDefault();
    const loginBtn = document.getElementById('loginBtn');
    const usernameValue = document.getElementById('username').value;
    const passwordValue = document.getElementById('password').value;

    loginBtn.disabled = true;
    loginBtn.innerText = 'Downloading...';

    try {
        const keyResponse = await fetch(`${BASE_URL}/public-key`);
        if (!keyResponse.ok) throw new Error('Could not get key');
        const { publicKey } = await keyResponse.json();

        const rsaKeyBuffer = base64ToArrayBuffer(publicKey);
        const serverPublicKey = await window.crypto.subtle.importKey(
            "spki", rsaKeyBuffer, { name: "RSA-OAEP", hash: "SHA-512" }, false, ["encrypt"]
        );

        const rawAesKeyBytes = window.crypto.getRandomValues(new Uint8Array(32));
        const aesKey = await window.crypto.subtle.importKey(
            "raw", rawAesKeyBytes, { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]
        );

        const loginPayload = JSON.stringify({ username: usernameValue, password: passwordValue });
        const encodedPayload = new TextEncoder().encode(loginPayload);
        const iv = window.crypto.getRandomValues(new Uint8Array(12));

        const encryptedDataBuffer = await window.crypto.subtle.encrypt(
            { name: "AES-GCM", iv: iv, tagLength: 128 },
            aesKey,
            encodedPayload
        );

        const encryptedDataArray = new Uint8Array(encryptedDataBuffer);
        const combinedPayload = new Uint8Array(iv.length + encryptedDataArray.length);
        combinedPayload.set(iv, 0);
        combinedPayload.set(encryptedDataArray, iv.length);
        const ciphertextBase64 = window.btoa(String.fromCharCode(...combinedPayload));

        const encryptedKeyBuffer = await window.crypto.subtle.encrypt(
            { name: "RSA-OAEP" },
            serverPublicKey,
            rawAesKeyBytes
        );
        const encryptedKeyBase64 = window.btoa(String.fromCharCode(...new Uint8Array(encryptedKeyBuffer)));

        const response = await fetch(`${BASE_URL}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                encryptedKey: encryptedKeyBase64,
                ciphertext: ciphertextBase64
            })
        });

        if (!response.ok) throw new Error('Authentication error');

        const data = await response.json();

        const serverEncryptedBytes = base64ToArrayBuffer(data.ciphertext);
        const serverIv = new Uint8Array(serverEncryptedBytes, 0, 12);
        const serverCiphertext = new Uint8Array(serverEncryptedBytes, 12);

        const decryptedResponseBuffer = await window.crypto.subtle.decrypt(
            { name: "AES-GCM", iv: serverIv, tagLength: 128 },
            aesKey,
            serverCiphertext
        );

        const decryptedResponseJson = new TextDecoder().decode(decryptedResponseBuffer);
        const responseData = JSON.parse(decryptedResponseJson);

        token = responseData.token;
        localStorage.setItem('token', token);

        parseTokenAndRole();
        showPage('dashboardPage');
        metricsCurrentPage = 1;
        fetchMetrics(1);
    } catch (error) {
        alert("Authentication error: " + error.message);
    } finally {
        loginBtn.disabled = false;
        loginBtn.innerText = 'Sign in';
    }
}

async function fetchCards() {
    try {
        const response = await fetch(`${BASE_URL}/cards`, {
            method: 'GET',
            cache: 'no-store',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Pragma': 'no-cache'
            }
        });
        if (!response.ok) {
            throw new Error('Failed to fetch');
        }
        allLoadedCards = await response.json();
        renderCards();
    } catch (err) {
        console.error("Fetch error:", err);
    }
}
function renderCards() {
    const container = document.getElementById('cardsContainer');
    container.innerHTML = '';

    if (userRole === 'ROLE_ADMIN') {
        container.style.display = 'block';
    } else {
        container.style.display = 'grid';
        container.style.gridTemplateColumns = 'repeat(4, 1fr)';
        container.style.gap = '15px';
    }

    if (!allLoadedCards || allLoadedCards.length === 0) {
        container.innerHTML = '<p style="padding:15px; color:#A0A8B5;">No cards yet</p>';
        updateCardsPaginationControls(1, 1);
        return;
    }

    const totalCardsPages = Math.ceil(allLoadedCards.length / cardsPageSize) || 1;
    if (cardsCurrentPage > totalCardsPages) cardsCurrentPage = totalCardsPages;

    const startIndex = (cardsCurrentPage - 1) * cardsPageSize;
    const endIndex = startIndex + cardsPageSize;
    const paginatedCards = allLoadedCards.slice(startIndex, endIndex);

    paginatedCards.forEach(card => {
        const cardItem = document.createElement('div');
        cardItem.className = 'card-item';

        const adminActions = (userRole === 'ROLE_ADMIN')
            ? `<div style="display: flex; gap: 5px;">
                 <button onclick="openEditCard('${card.id}')" style="background:#2e5685; border:none; color:white; padding:4px 8px; border-radius:4px; cursor:pointer; font-size: 0.75rem;">Edit</button>
                 <button onclick="handleDeleteCard('${card.id}')" style="background:#ff4d4d; border:none; color:white; padding:4px 8px; border-radius:4px; cursor:pointer; font-size: 0.75rem;">Remove</button>
               </div>`
            : '';

        cardItem.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px;">
                <h4 style="margin: 0;"><strong>${escapeHtml(card.title)}</strong></h4>
                ${adminActions}
            </div>
            <p style="color:#A0A8B5; font-family: monospace; margin: 5px 0;">Holder: ${escapeHtml(card.holderName) || 'Encrypted'}</p>
            <p style="color:var(--accent-yellow); font-family: monospace; font-size:1.1rem; letter-spacing:1.5px; word-break: break-all;">
                ${card.cardNumber || '•••• •••• •••• ••••'}
            </p>
            <small style="color:#4B6A7D;">CVV: ${card.cvv || '•••'}</small>
        `;
        container.appendChild(cardItem);
    });

    updateCardsPaginationControls(cardsCurrentPage, totalCardsPages);
}

function changeCardsPage(direction) {
    const totalCardsPages = Math.ceil(allLoadedCards.length / cardsPageSize) || 1;
    const targetPage = cardsCurrentPage + direction;
    if (targetPage >= 1 && targetPage <= totalCardsPages) {
        cardsCurrentPage = targetPage;
        renderCards();
    }
}

function updateCardsPaginationControls(current, total) {
    const infoSpan = document.getElementById('cardsPageInfo');
    const btnBack = document.getElementById('btnCardsBack');
    const btnNext = document.getElementById('btnCardsNext');

    if (infoSpan) infoSpan.innerText = `Page ${current} of ${total}`;
    if (btnBack) btnBack.disabled = current === 1;
    if (btnNext) btnNext.disabled = current === total || total === 0;
}

async function handleDeleteCard(cardId) {
    if (!confirm("Are you sure you want to drop this encrypted element?")) return;
    try {
        const response = await fetch(`${BASE_URL}/cards/detail?id=${cardId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error();
        fetchCards();
    } catch (err) {
        alert("Deletion failed.");
    }
}

async function fetchUsers() {
    try {
        const url = `${BASE_URL}/admin/users?page=${usersCurrentPage}&size=${USERS_PAGE_SIZE}&query=${encodeURIComponent(usersSearchQuery)}`;
        const response = await fetch(url, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error();
        const result = await response.json();

        usersCurrentPage = result.currentPage;
        usersTotalPages = result.totalPages || 1;
        renderUsers(result.data);
    } catch (err) {
        console.error("Failed to fetch users");
    }
}

function renderUsers(users) {
    const container = document.getElementById('usersContainer');
    container.innerHTML = '';

    if (!users || users.length === 0) {
        container.innerHTML = '<p style="padding:15px; color:#A0A8B5;">No users found</p>';
        updateUsersPaginationControls(1, 1);
        return;
    }

    users.forEach(user => {
        const userItem = document.createElement('div');
        userItem.className = 'card-item';

        const blockedStatus = user.isBlocked ? '<span style="color:#ff4d4d;">[BLOCKED]</span>' : '<span style="color:#4ecdc4;">[ACTIVE]</span>';

        userItem.innerHTML = `
            <h4><strong>${escapeHtml(user.username)}</strong> ${blockedStatus}</h4>
            <p style="color:#A0A8B5; font-family: monospace;">Role: ${escapeHtml(user.role)}</p>
            <div style="display: flex; gap: 8px; margin-top: 10px;">
                ${user.username !== currentUser ? `
                    <button onclick="handleUserAdminAction('block', ${!user.isBlocked}, '${escapeHtml(user.username)}')" 
                            style="background:${user.isBlocked ? '#4ecdc4' : '#FF9A42'}; border:none; color:black; padding:4px 8px; border-radius:4px; cursor:pointer;">
                        ${user.isBlocked ? 'Unblock' : 'Block'}
                    </button>
                    <button onclick="handleUserAdminAction('delete', null, '${escapeHtml(user.username)}')" 
                            style="background:#ff4d4d; border:none; color:white; padding:4px 8px; border-radius:4px; cursor:pointer;">
                        Delete
                    </button>
                ` : ''}
            </div>
        `;
        container.appendChild(userItem);
    });
}
function changeUsersPage(direction) {
    const targetPage = usersCurrentPage + direction;
    if (targetPage >= 1 && targetPage <= usersTotalPages) {
        usersCurrentPage = targetPage;
        fetchUsers();
    }
}

function updateUsersPaginationControls(current, total) {
    const infoSpan = document.getElementById('usersPageInfo');
    const btnBack = document.getElementById('btnUsersBack');
    const btnNext = document.getElementById('btnUsersNext');

    if (infoSpan) infoSpan.innerText = `Page ${current} of ${total}`;
    if (btnBack) btnBack.disabled = current === 1;
    if (btnNext) btnNext.disabled = current === total || total === 0;
}

async function handleCreateUser(e) {
    e.preventDefault();
    const username = document.getElementById('newUsername').value;
    const password = document.getElementById('newPassword').value;

    if (!/^[a-zA-Z0-9_]{3,20}$/.test(username)) {
        alert("Username must be 3-20 alphanumeric characters");
        return;
    }
    if (password.length < 6) {
        alert("Password must be at least 6 characters");
        return;
    }

    const payload = {
        action: "create",
        username: username,
        password: password,
        role: document.getElementById('newRole').value
    };

    try {
        const response = await fetch(`${BASE_URL}/admin/users`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || "Failed to create user");
        }
        alert("User registered!");
        document.getElementById('createUserForm').reset();
        fetchUsers();
    } catch (err) {
        alert(err.message);
    }
}

async function handleUserAdminAction(actionType, structuralState, targetUser) {
    if (!targetUser || !/^[a-zA-Z0-9_]{3,20}$/.test(targetUser)) {
        alert("Invalid target user.");
        return;
    }

    if (actionType === 'delete' && !confirm(`Are you sure you want to delete user ${targetUser}?`)) {
        return;
    }

    const payload = {
        action: actionType,
        username: targetUser,
        ...(actionType === 'block' && { block: structuralState })
    };

    try {
        const response = await fetch(`${BASE_URL}/admin/users`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || "Action failed");
        }
        fetchUsers();
    } catch (err) {
        alert(err.message);
    }
}

async function fetchMetrics(page) {
    const loader = document.getElementById('loader');
    const errorDiv = document.getElementById('errorMessage');

    loader.classList.remove('hidden');
    errorDiv.classList.add('hidden');

    try {
        const tableResponse = await fetch(`${BASE_URL}/metrics?page=${page}&size=${METRICS_PAGE_SIZE}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!tableResponse.ok) throw new Error();

        const tableResult = await tableResponse.json();
        metricsCurrentPage = tableResult.currentPage;
        metricsTotalPages = tableResult.totalPages || 1;

        renderTable(tableResult.data);

        const chartResponse = await fetch(`${BASE_URL}/metrics?page=1&size=100`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (chartResponse.ok) {
            const chartResult = await chartResponse.json();
            const allMetrics = chartResult.data || [];

            if (allMetrics.length > 0) {
                const times = allMetrics.map(metric => {
                    if (typeof metric.recordedAt === 'number') {
                        const date = new Date(metric.recordedAt * (metric.recordedAt < 99999999999 ? 1000 : 1));
                        return date.toLocaleTimeString('uk-UA');
                    } else {
                        const dateObj = new Date(metric.recordedAt);
                        return dateObj.toLocaleTimeString('uk-UA');
                    }
                });

                const latencies = allMetrics.map(metric =>
                    metric.durationNs ? BigInt(metric.durationNs).toString() : '0'
                );

                updateChartData(times.reverse(), latencies.reverse());
            } else {
                updateChartData([], []);
            }
        }

        updateMetricsPaginationControls();
    } catch (err) {
        errorDiv.innerText = 'Error fetching server performance metrics';
        errorDiv.classList.remove('hidden');
    } finally {
        loader.classList.add('hidden');
    }
}

function renderTable(metrics) {
    const tbody = document.getElementById('metricsTableBody');
    tbody.innerHTML = '';

    if (!metrics || metrics.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 20px;">No metrics recorded yet</td></tr>`;
        return;
    }

    metrics.forEach(metric => {
        const name = metric.methodName || '';
        const isHttp = name.startsWith('/') || name.includes('validate') || name.includes('load');
        const badge = isHttp ? '<span class="badge latency">HTTP API</span>' : '<span class="badge cpu">DB QUERY</span>';

        const latencyStr = metric.durationNs ? BigInt(metric.durationNs).toString() + ' ns' : '0 ns';

        let formattedDate = 'N/A';
        if (metric.recordedAt) {
            if (typeof metric.recordedAt === 'number') {
                const timestamp = metric.recordedAt * (metric.recordedAt < 99999999999 ? 1000 : 1);
                formattedDate = new Date(timestamp).toLocaleString('uk-UA');
            } else {
                formattedDate = new Date(metric.recordedAt).toLocaleString('uk-UA');
            }
        }

        const row = `
          <tr>
            <td><span class="status-active">${escapeHtml(metric.environment)}</span></td>
            <td><code>${escapeHtml(metric.hostName)}</code></td>
            <td>${badge}</td>
            <td><strong>${escapeHtml(name)}</strong></td>
            <td><span style="color: var(--accent-yellow); font-weight: bold;">${latencyStr}</span></td>
            <td>${formattedDate}</td>
          </tr>
        `;
        tbody.innerHTML += row;
    });
}

async function triggerAction(endpoint, buttonId, defaultText) {
    if (isActionLoading) return;

    isActionLoading = true;
    setGlobalButtonsState(true);
    document.getElementById(buttonId).innerText = 'Executing...';

    try {
        const response = await fetch(`${BASE_URL}/${endpoint}`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error();
        metricsCurrentPage = 1;
        await fetchMetrics(1);
    } catch (err) {
        alert(`Failed to trigger ${endpoint}`);
    } finally {
        isActionLoading = false;
        setGlobalButtonsState(false);
        document.getElementById(buttonId).innerText = defaultText;
    }
}

function changeMetricsPage(direction) {
    const targetPage = metricsCurrentPage + direction;
    if (targetPage >= 1 && targetPage <= metricsTotalPages) {
        fetchMetrics(targetPage);
    }
}

function updateMetricsPaginationControls() {
    document.getElementById('pageInfo').innerText = `Page ${metricsCurrentPage} of ${metricsTotalPages}`;
    document.getElementById('btnBack').disabled = metricsCurrentPage === 1 || isActionLoading;
    document.getElementById('btnNext').disabled = metricsCurrentPage === metricsTotalPages || metricsTotalPages === 0 || isActionLoading;
}

function setGlobalButtonsState(disabled) {
    document.getElementById('btnValidate').disabled = disabled;
    document.getElementById('btnLoad').disabled = disabled;
    document.getElementById('btnBack').disabled = disabled || metricsCurrentPage === 1;
    document.getElementById('btnNext').disabled = disabled || metricsCurrentPage === metricsTotalPages;
}

function handleLogout() {
    localStorage.removeItem('token');
    token = null;
    userRole = null;
    currentUser = null;
    showPage('loginPage');
    document.getElementById('username').value = '';
    document.getElementById('password').value = '';

    if (metricsChart) {
        metricsChart.destroy();
        metricsChart = null;
    }
}

function initChart() {
    const canvas = document.getElementById('metricsChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(255, 154, 66, 0.4)');
    gradient.addColorStop(1, 'rgba(255, 154, 66, 0.0)');

    metricsChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Latency (ns)',
                data: [],
                borderColor: '#FF9A42',
                backgroundColor: gradient,
                borderWidth: 2,
                pointBackgroundColor: '#FF9A42',
                pointBorderColor: '#1B2430',
                pointBorderWidth: 2,
                pointRadius: 4,
                pointHoverRadius: 6,
                fill: true,
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { labels: { color: '#F5F7FA' }, onClick: null },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    backgroundColor: 'rgba(15, 23, 32, 0.9)',
                    titleColor: '#F5F7FA',
                    bodyColor: '#FFD58A',
                    borderColor: '#4B6A7D',
                    borderWidth: 1,
                    padding: 10
                }
            },
            scales: {
                x: {
                    grid: { color: 'rgba(75, 106, 125, 0.2)', drawBorder: false },
                    ticks: { color: '#A0A8B5' }
                },
                y: {
                    grid: { color: 'rgba(75, 106, 125, 0.2)', drawBorder: false },
                    ticks: { color: '#A0A8B5' },
                    beginAtZero: true
                }
            },
            interaction: { mode: 'nearest', axis: 'x', intersect: false }
        }
    });
}

function updateChartData(newLabels, newData) {
    if (metricsChart) {
        metricsChart.data.labels = newLabels;
        metricsChart.data.datasets[0].data = newData;
        metricsChart.update();
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function openEditCard(cardId) {
    const card = allLoadedCards.find(c => c.id === cardId);
    if (!card) {
        return;
    }
    document.getElementById('cardTitle').value = card.title;
    document.getElementById('cardHolder').value = card.holderName;
    document.getElementById('cardNumber').value = card.cardNumber;
    document.getElementById('cardCvv').value = card.cvv;
    document.getElementById('secureCardForm').dataset.editId = cardId;
    document.getElementById('formTitle').innerText = 'Edit card';
    document.getElementById('btnCancelEdit').classList.remove('hidden');
}

function cancelEdit() {
    const form = document.getElementById('secureCardForm');
    form.reset();
    delete form.dataset.editId;
    document.getElementById('formTitle').innerText = 'Add new card';
    document.getElementById('btnCancelEdit').classList.add('hidden');
}

async function handleSaveCard(e) {
    e.preventDefault();
    const form = e.target;
    const editId = form.dataset.editId;
    const payload = {
        title: document.getElementById('cardTitle').value,
        holderName: document.getElementById('cardHolder').value,
        cardNumber: document.getElementById('cardNumber').value,
        cvv: document.getElementById('cardCvv').value
    };

    const method = editId ? 'PUT' : 'POST';
    const url = editId ? `${BASE_URL}/cards/detail?id=${editId}` : `${BASE_URL}/cards`;

    try {
        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || "Failed to save card");
        }

        form.reset();
        cancelEdit();
        fetchCards();
    } catch (err) {
        alert(err.message);
    }
}
