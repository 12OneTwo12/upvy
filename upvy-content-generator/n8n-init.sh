#!/bin/sh
# =============================================================================
# n8n 초기화 스크립트
# =============================================================================
# Credential 자동 생성 + 워크플로우 자동 import
#
# 주의: n8n은 첫 실행시 Owner 계정 설정이 필수입니다 (보안 요구사항)
#       설정 후 볼륨이 유지되면 다시 설정할 필요 없습니다
# =============================================================================

WORKFLOW_FILE="/workflows/content-generator.json"
MARKER_FILE="/home/node/.n8n/.workflow-imported"
CRED_MARKER_FILE="/home/node/.n8n/.credentials-created"
GCP_KEY_FILE="/credentials/gcp-key.json"

# n8n API 호출을 위한 설정
N8N_URL="http://localhost:5678"
AUTH_USER="${N8N_BASIC_AUTH_USER:-admin}"
AUTH_PASS="${N8N_BASIC_AUTH_PASSWORD}"

# =============================================================================
# Credential 생성 함수 (Node.js 사용 - JSON escape 안전)
# =============================================================================
create_credentials() {
    echo "[init] Creating credentials via n8n API..."

    node << 'NODEJS_SCRIPT'
const http = require('http');
const fs = require('fs');
const path = require('path');

const N8N_URL = 'http://localhost:5678';
const AUTH = Buffer.from(`${process.env.N8N_BASIC_AUTH_USER || 'admin'}:${process.env.N8N_BASIC_AUTH_PASSWORD}`).toString('base64');

// 세션 쿠키 저장
let sessionCookie = '';

// n8n 로그인 (세션 쿠키 획득)
async function login() {
    return new Promise((resolve) => {
        const payload = JSON.stringify({
            emailOrLdapLoginId: process.env.N8N_OWNER_EMAIL || 'admin@upvy.io',
            password: process.env.N8N_BASIC_AUTH_PASSWORD
        });

        const options = {
            hostname: 'localhost',
            port: 5678,
            path: '/rest/login',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload)
            }
        };

        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                if (res.statusCode === 200) {
                    // 쿠키 추출
                    const cookies = res.headers['set-cookie'];
                    if (cookies) {
                        sessionCookie = cookies.map(c => c.split(';')[0]).join('; ');
                    }
                    console.log('[init] ✓ Logged in to n8n');
                    resolve(true);
                } else {
                    console.log(`[init] ✗ Login failed: ${res.statusCode} - ${body}`);
                    resolve(false);
                }
            });
        });

        req.on('error', (e) => {
            console.log(`[init] ✗ Login error: ${e.message}`);
            resolve(false);
        });

        req.write(payload);
        req.end();
    });
}

// Credential 생성 API 호출
async function createCredential(name, type, data) {
    return new Promise((resolve, reject) => {
        const payload = JSON.stringify({ name, type, data });

        const options = {
            hostname: 'localhost',
            port: 5678,
            path: '/rest/credentials',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
                'Cookie': sessionCookie
            }
        };

        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                if (res.statusCode === 200 || res.statusCode === 201) {
                    console.log(`[init] ✓ Credential '${name}' created`);
                    resolve(true);
                } else if (res.statusCode === 409 || body.includes('already exists')) {
                    console.log(`[init] ✓ Credential '${name}' already exists`);
                    resolve(true);
                } else {
                    console.log(`[init] ✗ Failed to create '${name}': ${res.statusCode} - ${body}`);
                    resolve(false);
                }
            });
        });

        req.on('error', (e) => {
            console.log(`[init] ✗ Error creating '${name}': ${e.message}`);
            resolve(false);
        });

        req.write(payload);
        req.end();
    });
}

async function main() {
    const credentials = [];

    // 1. Google API Credential (from gcp-key.json)
    const gcpKeyPath = '/credentials/gcp-key.json';
    if (fs.existsSync(gcpKeyPath)) {
        try {
            const gcpKey = JSON.parse(fs.readFileSync(gcpKeyPath, 'utf8'));
            credentials.push({
                name: 'Google API',
                type: 'googleApi',
                data: {
                    email: gcpKey.client_email,
                    privateKey: gcpKey.private_key
                }
            });
            console.log('[init] Found GCP key file');
        } catch (e) {
            console.log('[init] Warning: Failed to parse GCP key file:', e.message);
        }
    }

    // 2. AWS Credential (from environment variables)
    if (process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY) {
        credentials.push({
            name: 'AWS',
            type: 'aws',
            data: {
                accessKeyId: process.env.AWS_ACCESS_KEY_ID,
                secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
                region: process.env.AWS_REGION || 'ap-northeast-2'
            }
        });
        console.log('[init] Found AWS credentials in env');
    }

    // 3. Telegram Credential (from environment variables)
    if (process.env.TELEGRAM_BOT_TOKEN) {
        credentials.push({
            name: 'Telegram API',
            type: 'telegramApi',
            data: {
                accessToken: process.env.TELEGRAM_BOT_TOKEN
            }
        });
        console.log('[init] Found Telegram token in env');
    }

    // 4. Instagram/Facebook Credential (from environment variables)
    if (process.env.INSTAGRAM_ACCESS_TOKEN) {
        credentials.push({
            name: 'Facebook Graph API',
            type: 'facebookGraphApi',
            data: {
                accessToken: process.env.INSTAGRAM_ACCESS_TOKEN
            }
        });
        console.log('[init] Found Instagram token in env');
    }

    if (credentials.length === 0) {
        console.log('[init] No credentials to create');
        return;
    }

    // 먼저 로그인
    const loggedIn = await login();
    if (!loggedIn) {
        console.log('[init] ✗ Cannot create credentials without login (Owner setup may be required)');
        console.log('[init] → Please complete Owner setup in n8n UI first, then restart');
        process.exit(1);
    }

    console.log(`[init] Creating ${credentials.length} credential(s)...`);

    for (const cred of credentials) {
        await createCredential(cred.name, cred.type, cred.data);
    }

    console.log('[init] ✓ Credentials setup completed');
    process.exit(0);
}

main().catch(e => {
    console.error('[init] ✗ Credential setup failed:', e.message);
    process.exit(1);
});
NODEJS_SCRIPT
}

# =============================================================================
# 백그라운드 초기화 작업
# =============================================================================
init_background() {
    (
        echo "[init] Waiting for n8n to start..."
        sleep 20

        # n8n 준비 대기
        for i in 1 2 3 4 5 6 7 8 9 10; do
            if wget -q --spider http://localhost:5678/healthz 2>/dev/null; then
                echo "[init] n8n is ready!"
                break
            fi
            echo "[init] Waiting... ($i/10)"
            sleep 5
        done

        # Credential 생성 (성공할 때까지 재시도 가능)
        if [ ! -f "$CRED_MARKER_FILE" ]; then
            if create_credentials; then
                touch "$CRED_MARKER_FILE"
            fi
            # 실패해도 marker 파일 생성 안 함 → 재시작 시 재시도
        else
            echo "[init] Credentials already configured"
        fi

        # 워크플로우 import (API로 중복 체크)
        if [ -f "$WORKFLOW_FILE" ]; then
            # 이미 같은 이름의 workflow가 있는지 API로 확인
            WORKFLOW_EXISTS=$(node -e "
                const http = require('http');
                const options = {
                    hostname: 'localhost',
                    port: 5678,
                    path: '/rest/workflows',
                    headers: { 'Cookie': '$(cat /tmp/n8n-cookie 2>/dev/null || echo "")' }
                };
                http.get(options, res => {
                    let data = '';
                    res.on('data', chunk => data += chunk);
                    res.on('end', () => {
                        try {
                            const workflows = JSON.parse(data).data || [];
                            const exists = workflows.some(w => w.name === 'Upvy AI Content Generator');
                            console.log(exists ? 'yes' : 'no');
                        } catch(e) { console.log('no'); }
                    });
                }).on('error', () => console.log('no'));
            " 2>/dev/null)

            if [ "$WORKFLOW_EXISTS" = "yes" ]; then
                echo "[init] Workflow 'Upvy AI Content Generator' already exists, skipping import"
                touch "$MARKER_FILE"
            else
                echo "[init] Importing workflow from $WORKFLOW_FILE..."
                if /usr/local/bin/n8n import:workflow --input="$WORKFLOW_FILE" 2>&1; then
                    touch "$MARKER_FILE"
                    echo "[init] Workflow imported successfully!"
                else
                    echo "[init] Warning: Failed to import workflow (may need owner setup first)"
                fi
            fi
        fi
    ) &
}

# 백그라운드 작업 시작
init_background

# 기본 n8n 엔트리포인트 실행
exec /docker-entrypoint.sh "$@"
