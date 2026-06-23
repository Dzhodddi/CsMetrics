const BASE_URL = '/api/v1';
let token = localStorage.getItem('token') || null;
let isActionLoading = false;
let metricsChart;
let userRole = null;

let metricsCurrentPage = 1;
let metricsTotalPages = 1;
const METRICS_PAGE_SIZE = 2;

let cardsCurrentPage = 1;
const CARDS_PAGE_SIZE = 2;
let allLoadedCards = [];

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

    document.getElementById('btnLogout').addEventListener('click', handleLogout);
    document.getElementById('secureCardForm').addEventListener('submit', handleAddCard);
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

        if (userRole === 'ROLE_ADMIN') {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.remove('hidden'));
        } else {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.add('hidden'));
        }
    } catch (e) {
        console.error("Failed to extract claims from JWT metadata wrapper", e);
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
}

async function handleLogin(e) {
    e.preventDefault();
    const loginBtn = document.getElementById('loginBtn');
    const usernameValue = document.getElementById('username').value;
    const passwordValue = document.getElementById('password').value;

    loginBtn.disabled = true;
    loginBtn.innerText = 'Downloading...';

    try {
        const response = await fetch(`${BASE_URL}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: usernameValue, password: passwordValue })
        });

        if (!response.ok) throw new Error('Error while creating login');

        const data = await response.json();
        token = data.token;
        localStorage.setItem('token', token);

        parseTokenAndRole();
        showPage('dashboardPage');
        metricsCurrentPage = 1;
        fetchMetrics(1);
    } catch (error) {
        alert("Error logging in");
    } finally {
        loginBtn.disabled = false;
        loginBtn.innerText = 'Enter';
    }
}

async function fetchCards() {
    try {
        const response = await fetch(`${BASE_URL}/cards`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error();
        allLoadedCards = await response.json();
        renderCards();
    } catch (err) {
        console.error("Failed to fetch protected cards details", err);
    }
}

function renderCards() {
    const container = document.getElementById('cardsContainer');
    container.innerHTML = '';

    if (!allLoadedCards || allLoadedCards.length === 0) {
        container.innerHTML = '<p style="padding:15px; color:#A0A8B5;">No cards yet</p>';
        updateCardsPaginationControls(1, 1);
        return;
    }

    const totalCardsPages = Math.ceil(allLoadedCards.length / CARDS_PAGE_SIZE) || 1;
    if (cardsCurrentPage > totalCardsPages) cardsCurrentPage = totalCardsPages;

    const startIndex = (cardsCurrentPage - 1) * CARDS_PAGE_SIZE;
    const endIndex = startIndex + CARDS_PAGE_SIZE;
    const paginatedCards = allLoadedCards.slice(startIndex, endIndex);

    paginatedCards.forEach(card => {
        const cardItem = document.createElement('div');
        cardItem.className = 'card-item';
        cardItem.style = 'background: #1B2430; padding: 15px; border-radius: 6px; margin-bottom: 10px; border-left: 4px solid var(--accent-yellow); position: relative;';

        const deleteBtn = userRole === 'ROLE_ADMIN'
            ? `<button onclick="handleDeleteCard('${card.id}')" style="background:#ff4d4d; border:none; color:white; padding:4px 8px; border-radius:4px; cursor:pointer; position:absolute; top:15px; right:15px;">Видалити</button>`
            : '';

        cardItem.innerHTML = `
            ${deleteBtn}
            <h4><strong>${escapeHtml(card.title)}</strong></h4>
            <p style="color:#A0A8B5; font-family: monospace; margin: 5px 0;">Holder: ${escapeHtml(card.holderName) || 'Encrypted / Unavailable'}</p>
            <p style="color:var(--accent-yellow); font-family: monospace; font-size:1.1rem; letter-spacing:1.5px;">
                ${card.cardNumber || '•••• •••• •••• ••••'}
            </p>
            <small style="color:#4B6A7D;">CVV: ${card.cvv || '•••'}</small>
        `;
        container.appendChild(cardItem);
    });

    updateCardsPaginationControls(cardsCurrentPage, totalCardsPages);
}

function changeCardsPage(direction) {
    const totalCardsPages = Math.ceil(allLoadedCards.length / CARDS_PAGE_SIZE) || 1;
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

async function handleAddCard(e) {
    e.preventDefault();
    const payload = {
        title: document.getElementById('cardTitle').value,
        holderName: document.getElementById('cardHolder').value,
        cardNumber: document.getElementById('cardNumber').value,
        cvv: document.getElementById('cardCvv').value
    };

    try {
        const response = await fetch(`${BASE_URL}/cards`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            throw new Error();
        }

        document.getElementById('secureCardForm').reset();
        cardsCurrentPage = 1;
        fetchCards();
    } catch (err) {
        alert("Failed to store encrypted parameters inside target database framework.");
    }
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

async function handleCreateUser(e) {
    e.preventDefault();
    const payload = {
        action: "create",
        username: document.getElementById('newUsername').value,
        password: document.getElementById('newPassword').value,
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
            throw new Error();
        }
        alert("User registered cleanly.");
        document.getElementById('createUserForm').reset();
    } catch (err) {
        alert("Failed to generate custom profile parameters natively.");
    }
}

async function handleUserAdminAction(actionType, structuralState) {
    const targetUser = document.getElementById('actionUsername').value;
    if (!targetUser) {
        alert("Please specify a target username.");
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
        if (!response.ok) throw new Error();
        alert(`Action ${actionType} completed successfully.`);
        document.getElementById('actionUsername').value = '';
    } catch (err) {
        alert("Administrative execution failed.");
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

        if (!tableResponse.ok) {
            throw new Error('Error while fetching table metrics');
        }

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
                        const safeDateStr = metric.recordedAt.replace('T', ' ').replace(/-/g, '/');
                        const date = new Date(safeDateStr);
                        return date.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                    }
                });

                const latencies = allMetrics.map(metric =>
                    metric.durationNs ? (metric.durationNs / 1000000).toFixed(2) : 0
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
        const latency = metric.durationNs ? (metric.durationNs / 1000000).toFixed(2) : '0.00';

        let formattedDate = 'N/A';
        if (metric.recordedAt) {
            if (typeof metric.recordedAt === 'number') {
                const timestamp = metric.recordedAt * (metric.recordedAt < 99999999999 ? 1000 : 1);
                formattedDate = new Date(timestamp).toLocaleString('uk-UA');
            } else {
                const safeDateStr = metric.recordedAt.replace('T', ' ').replace(/-/g, '/');
                formattedDate = new Date(safeDateStr).toLocaleString('uk-UA');
            }
        }

        const row = `
          <tr>
            <td><span class="status-active">${escapeHtml(metric.environment)}</span></td>
            <td><code>${escapeHtml(metric.hostName)}</code></td>
            <td>${badge}</td>
            <td><strong>${escapeHtml(name)}</strong></td>
            <td><span style="color: var(--accent-yellow); font-weight: bold;">${latency} ms</span></td>
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
                label: 'Latency (ms)',
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
                legend: { labels: { color: '#F5F7FA' } },
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
