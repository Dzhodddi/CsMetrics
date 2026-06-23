const BASE_URL = 'http://127.0.0.1:8080/api/v1';
let token = localStorage.getItem('token') || null;
let currentPage = 1;
let totalPages = 1;
let isActionLoading = false;

window.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    if (token) {
        showPage('dashboardPage');
        fetchMetrics(currentPage);
    } else {
        showPage('loginPage');
    }
});

function setupEventListeners() {
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('btnValidate').addEventListener('click', () => triggerAction('validate', 'btnValidate', 'Trigger Validate'));
    document.getElementById('btnLoad').addEventListener('click', () => triggerAction('load-data', 'btnLoad', 'Trigger Load Data'));
    document.getElementById('btnBack').addEventListener('click', () => changePage(-1));
    document.getElementById('btnNext').addEventListener('click', () => changePage(1));
    document.getElementById('btnLogout').addEventListener('click', handleLogout);
}

function showPage(pageId) {
    document.getElementById('loginPage').classList.add('hidden');
    document.getElementById('dashboardPage').classList.add('hidden');
    document.getElementById(pageId).classList.remove('hidden');
}

async function handleLogin(e) {
    e.preventDefault();
    const loginBtn = document.getElementById('loginBtn');
    loginBtn.disabled = true;
    loginBtn.innerText = 'Downloading...';

    const usernameInput = document.getElementById('username').value;
    const passwordInput = document.getElementById('password').value;

    try {
        const response = await fetch(`${BASE_URL}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: usernameInput, password: passwordInput })
        });

        if (!response.ok) throw new Error('Error while creating login');

        const data = await response.json();
        token = data.token;
        localStorage.setItem('token', token);

        showPage('dashboardPage');
        fetchMetrics(1);
    } catch (error) {
        alert("Error logging in");
    } finally {
        loginBtn.disabled = false;
        loginBtn.innerText = 'Enter';
    }
}

async function fetchMetrics(page) {
    const loader = document.getElementById('loader');
    const errorDiv = document.getElementById('errorMessage');

    loader.classList.remove('hidden');
    errorDiv.classList.add('hidden');

    try {
        const response = await fetch(`${BASE_URL}/metrics?page=${page}&size=8`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) {
            throw new Error('Error while fetching metrics');
        }

        const result = await response.json();
        currentPage = result.currentPage;
        totalPages = result.totalPages || 1;

        renderTable(result.data);
        updatePaginationControls();
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
            formattedDate = typeof metric.recordedAt === 'number'
                ? new Date(metric.recordedAt * (metric.recordedAt < 99999999999 ? 1000 : 1)).toLocaleString()
                : new Date(metric.recordedAt).toLocaleString();
        }

        const row = `
      <tr>
        <td><span class="status-active">${metric.environment}</span></td>
        <td><code>${metric.hostName}</code></td>
        <td>${badge}</td>
        <td><strong>${name}</strong></td>
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
        await fetchMetrics(1);
    } catch (err) {
        alert(`Failed to trigger ${endpoint}`);
    } finally {
        isActionLoading = false;
        setGlobalButtonsState(false);
        document.getElementById(buttonId).innerText = defaultText;
    }
}

function changePage(direction) {
    const targetPage = currentPage + direction;
    if (targetPage >= 1 && targetPage <= totalPages) {
        fetchMetrics(targetPage);
    }
}

function updatePaginationControls() {
    document.getElementById('pageInfo').innerText = `Page ${currentPage} of ${totalPages}`;
    document.getElementById('btnBack').disabled = currentPage === 1 || isActionLoading;
    document.getElementById('btnNext').disabled = currentPage === totalPages || totalPages === 0 || isActionLoading;
}

function setGlobalButtonsState(disabled) {
    document.getElementById('btnValidate').disabled = disabled;
    document.getElementById('btnLoad').disabled = disabled;
    document.getElementById('btnBack').disabled = disabled || currentPage === 1;
    document.getElementById('btnNext').disabled = disabled || currentPage === totalPages;
}

function handleLogout() {
    localStorage.removeItem('token');
    token = null;
    showPage('loginPage');
    document.getElementById('username').value = '';
    document.getElementById('password').value = '';
}
