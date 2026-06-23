const BASE_URL = 'http://127.0.0.1:8080/api/v1';
let token = localStorage.getItem('token') || null;
let currentPage = 1;
let totalPages = 1;
let isActionLoading = false;

// Генеруємо випадковий унікальний ID клієнта для сесії (потрібен для SessionKeyStore на бекенді)
const CLIENT_ID = Math.floor(Math.random() * 1000000);
let aesKeyInstance = null; // Тут зберігатиметься згенерований AES ключ

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

// === КРИПТОГРАФІЧНІ ХЕЛПЕРИ (Web Crypto API) ===

// Конвертація рядка Base64 в ArrayBuffer
function base64ToArrayBuffer(base64) {
    const binaryString = window.atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}

// Конвертація ArrayBuffer в рядок Base64
function arrayBufferToBase64(buffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
}

async function performCryptoHandshake() {
    // 1. Отримуємо публічний RSA ключ від сервера
    const pkResponse = await fetch(`${BASE_URL}/crypto/public-key`);
    const pkData = await pkResponse.json();
    const serverPublicKeyBuffer = base64ToArrayBuffer(pkData.publicKey);

    // ЗМІНЕНО: Спробуємо імпортувати як RSA-OAEP з SHA-1 або RSA-PKCS1-v1_5
    // Якщо твоя Java використовує стандартний RsaUtil, найчастіше там дефолтний RSA/ECB/PKCS1Padding
    let serverPublicKey;
    let encryptedAesKeyBuffer;

    // Генеруємо симетричний AES-256 ключ
    aesKeyInstance = await window.crypto.subtle.generateKey(
        { name: "AES-CBC", length: 256 },
        true,
        ["encrypt", "decrypt"]
    );
    const rawAesKeyBytes = await window.crypto.subtle.exportKey("raw", aesKeyInstance);

    try {
        // Пробуємо найпопулярніший в Java дефолтний варіант: RSA-PKCS1-v1_5
        // У функції performCryptoHandshake() залиште тільки імпорт для RSA-OAEP:
        serverPublicKey = await window.crypto.subtle.importKey(
            "spki",
            serverPublicKeyBuffer,
            { name: "RSA-OAEP", hash: "SHA-1" },
            false,
            ["encrypt"]
        );

        encryptedAesKeyBuffer = await window.crypto.subtle.encrypt(
            { name: "RSA-OAEP" },
            serverPublicKey,
            rawAesKeyBytes
        );
    } catch (e) {
        // Якщо браузер закричав, що хоче OAEP (наприклад, у Chrome), робимо fallback на SHA-1
        serverPublicKey = await window.crypto.subtle.importKey(
            "spki",
            serverPublicKeyBuffer,
            { name: "RSA-OAEP", hash: "SHA-1" },
            false,
            ["encrypt"]
        );
        encryptedAesKeyBuffer = await window.crypto.subtle.encrypt(
            { name: "RSA-OAEP" },
            serverPublicKey,
            rawAesKeyBytes
        );
    }

    const encryptedAesKeyBase64 = arrayBufferToBase64(encryptedAesKeyBuffer);

    // 2. ВІДПРАВЛЯЄМО: Змінено тип clientId на ЧИСЛО (Number), щоб Java не падала при парсингу JSON!
    const handshakeResponse = await fetch(`${BASE_URL}/crypto/handshake`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            clientId: Number(CLIENT_ID), // <--- Тепер це суто інтове число, а не рядок "407026"
            encryptedAesKey: encryptedAesKeyBase64
        })
    });

    if (!handshakeResponse.ok) throw new Error("Handshake failed on server side");
}

async function handleLogin(e) {
    e.preventDefault();
    const loginBtn = document.getElementById('loginBtn');
    loginBtn.disabled = true;
    loginBtn.innerText = 'Downloading...';

    const usernameInput = document.getElementById('username').value;
    const passwordInput = document.getElementById('password').value;

    try {
        // Крок 1: Обмін ключами перед відправкою логіну
        await performCryptoHandshake();

        // Крок 2: Шифруємо дані логіну по AES (IV заповнюємо нулями відповідно до дефолтної логіки AesUtil в Java)
        const rawJsonPayload = JSON.stringify({ username: usernameInput, password: passwordInput });
        const encoder = new TextEncoder();
        const dataBytes = encoder.encode(rawJsonPayload);
        const iv = new Uint8Array(16); // 16 нульових байтів

        const encryptedDataBuffer = await window.crypto.subtle.encrypt(
            { name: "AES-CBC", iv: iv },
            aesKeyInstance,
            dataBytes
        );
        const ciphertextBase64 = arrayBufferToBase64(encryptedDataBuffer);

        // Крок 3: Надсилаємо зашифроване тіло разом із заголовком клієнта
        const response = await fetch(`${BASE_URL}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Client-ID': CLIENT_ID.toString()
            },
            body: JSON.stringify({ ciphertext: ciphertextBase64 })
        });

        if (!response.ok) throw new Error('Invalid credentials or session error');

        const responseData = await response.json();

        // Крок 4: Дешифруємо відповідь сервера (там лежить JWT)
        const encryptedResponseBuffer = base64ToArrayBuffer(responseData.ciphertext);
        const decryptedResponseBuffer = await window.crypto.subtle.decrypt(
            { name: "AES-CBC", iv: iv },
            aesKeyInstance,
            encryptedResponseBuffer
        );

        const decoder = new TextDecoder();
        const decryptedJsonString = decoder.decode(decryptedResponseBuffer);
        const tokenData = JSON.parse(decryptedJsonString);

        token = tokenData.token;
        localStorage.setItem('token', token);

        showPage('dashboardPage');
        fetchMetrics(1);
    } catch (error) {
        console.error(error);
        alert("Authentication failed: Check credentials or secure connection");
    } finally {
        loginBtn.disabled = false;
        loginBtn.innerText = 'Enter';
    }
}

// Решта логіки (метрики, пагінація) залишається без змін, оскільки вони захищені стандартним Bearer JWT
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
