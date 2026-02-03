#!/bin/sh
# =============================================================================
# n8n 초기화 스크립트
# =============================================================================
# Credential 자동 생성 + 워크플로우 자동 import + Credential 자동 연결
#
# 주의: n8n은 첫 실행시 Owner 계정 설정이 필수입니다 (보안 요구사항)
#       설정 후 볼륨이 유지되면 다시 설정할 필요 없습니다
# =============================================================================

WORKFLOW_FILE="/workflows/content-generator.json"
MARKER_FILE="/home/node/.n8n/.workflow-imported"
CRED_MARKER_FILE="/home/node/.n8n/.credentials-created"
GCP_KEY_FILE="/credentials/gcp-key.json"

# =============================================================================
# 통합 초기화 함수 (Node.js)
# =============================================================================
run_init() {
    node << 'NODEJS_SCRIPT'
const http = require('http');
const fs = require('fs');

let sessionCookie = '';
const createdCredentials = {}; // name -> id 매핑

// =============================================================================
// HTTP 요청 헬퍼
// =============================================================================
function httpRequest(method, path, body = null) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'localhost',
            port: 5678,
            path,
            method,
            headers: {
                'Content-Type': 'application/json',
                'Cookie': sessionCookie
            }
        };

        if (body) {
            const payload = JSON.stringify(body);
            options.headers['Content-Length'] = Buffer.byteLength(payload);
        }

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                // 쿠키 저장
                if (res.headers['set-cookie']) {
                    sessionCookie = res.headers['set-cookie'].map(c => c.split(';')[0]).join('; ');
                }
                resolve({ status: res.statusCode, data, headers: res.headers });
            });
        });

        req.on('error', reject);
        if (body) req.write(JSON.stringify(body));
        req.end();
    });
}

// =============================================================================
// Owner 계정 설정 (첫 실행 시)
// =============================================================================
async function setupOwner() {
    console.log('[init] Checking owner setup...');
    const res = await httpRequest('POST', '/rest/owner/setup', {
        email: process.env.N8N_OWNER_EMAIL || 'admin@upvy.io',
        password: process.env.N8N_BASIC_AUTH_PASSWORD,
        firstName: process.env.N8N_OWNER_FIRSTNAME || 'Admin',
        lastName: process.env.N8N_OWNER_LASTNAME || 'User'
    });

    if (res.status === 200 || res.status === 201) {
        console.log('[init] ✓ Owner account created');
        return true;
    } else if (res.status === 400 && res.data.includes('already')) {
        console.log('[init] ✓ Owner already exists');
        return true;
    }
    console.log(`[init] Owner setup response: ${res.status} - ${res.data}`);
    return true; // 계속 진행
}

// =============================================================================
// 로그인
// =============================================================================
async function login() {
    console.log('[init] Logging in to n8n...');
    const res = await httpRequest('POST', '/rest/login', {
        emailOrLdapLoginId: process.env.N8N_OWNER_EMAIL || 'admin@upvy.io',
        password: process.env.N8N_BASIC_AUTH_PASSWORD
    });

    if (res.status === 200) {
        console.log('[init] ✓ Logged in');
        return true;
    }
    console.log(`[init] ✗ Login failed: ${res.status} - ${res.data}`);
    return false;
}

// =============================================================================
// Credential 생성 (ID 반환)
// =============================================================================
async function createCredential(name, type, data) {
    const res = await httpRequest('POST', '/rest/credentials', { name, type, data });

    if (res.status === 200 || res.status === 201) {
        try {
            const result = JSON.parse(res.data);
            const id = result.data?.id || result.id;
            console.log(`[init] ✓ Credential '${name}' created (id: ${id})`);
            return id;
        } catch (e) {
            console.log(`[init] ✓ Credential '${name}' created (id parse failed)`);
            return null;
        }
    } else if (res.status === 409 || res.data.includes('already exists')) {
        // 이미 존재하면 ID 조회
        const listRes = await httpRequest('GET', '/rest/credentials');
        if (listRes.status === 200) {
            try {
                const creds = JSON.parse(listRes.data).data || [];
                const existing = creds.find(c => c.name === name);
                if (existing) {
                    console.log(`[init] ✓ Credential '${name}' already exists (id: ${existing.id})`);
                    return existing.id;
                }
            } catch (e) {}
        }
        console.log(`[init] ✓ Credential '${name}' already exists`);
        return null;
    }

    console.log(`[init] ✗ Failed to create '${name}': ${res.status}`);
    return null;
}

// =============================================================================
// 워크플로우 조회
// =============================================================================
async function getWorkflowByName(name) {
    const res = await httpRequest('GET', '/rest/workflows');
    if (res.status === 200) {
        try {
            const workflows = JSON.parse(res.data).data || [];
            return workflows.find(w => w.name === name);
        } catch (e) {}
    }
    return null;
}

// =============================================================================
// 워크플로우 상세 조회
// =============================================================================
async function getWorkflowById(id) {
    const res = await httpRequest('GET', `/rest/workflows/${id}`);
    if (res.status === 200) {
        try {
            return JSON.parse(res.data).data || JSON.parse(res.data);
        } catch (e) {}
    }
    return null;
}

// =============================================================================
// 워크플로우 업데이트 (Credential 연결)
// =============================================================================
async function updateWorkflowCredentials(workflowId, credentialMapping) {
    const workflow = await getWorkflowById(workflowId);
    if (!workflow) {
        console.log('[init] ✗ Failed to get workflow for update');
        return false;
    }

    let updated = false;
    for (const node of workflow.nodes) {
        // Telegram 노드 처리
        if (node.type === 'n8n-nodes-base.telegram' && credentialMapping.telegramApi) {
            node.credentials = {
                telegramApi: {
                    id: credentialMapping.telegramApi,
                    name: 'Telegram API'
                }
            };
            updated = true;
            console.log(`[init] ✓ Linked Telegram credential to node '${node.name}'`);
        }

        // AWS S3 노드 처리
        if (node.type === 'n8n-nodes-base.awsS3' && credentialMapping.aws) {
            node.credentials = {
                aws: {
                    id: credentialMapping.aws,
                    name: 'AWS'
                }
            };
            updated = true;
            console.log(`[init] ✓ Linked AWS credential to node '${node.name}'`);
        }
    }

    if (!updated) {
        console.log('[init] No credentials to link');
        return true;
    }

    // 워크플로우 업데이트
    const res = await httpRequest('PATCH', `/rest/workflows/${workflowId}`, {
        nodes: workflow.nodes,
        connections: workflow.connections,
        settings: workflow.settings
    });

    if (res.status === 200) {
        console.log('[init] ✓ Workflow credentials updated');
        return true;
    }

    console.log(`[init] ✗ Failed to update workflow: ${res.status} - ${res.data}`);
    return false;
}

// =============================================================================
// 메인
// =============================================================================
async function main() {
    // 1. Owner 계정 설정 (첫 실행 시)
    await setupOwner();

    // 2. 로그인
    if (!await login()) {
        console.log('[init] ✗ Cannot proceed without login');
        process.exit(1);
    }

    // 2. Credential 생성
    console.log('[init] Creating credentials...');
    const credentialMapping = {};

    // Telegram
    if (process.env.TELEGRAM_BOT_TOKEN) {
        const id = await createCredential('Telegram API', 'telegramApi', {
            accessToken: process.env.TELEGRAM_BOT_TOKEN
        });
        if (id) credentialMapping.telegramApi = id;
    }

    // AWS
    if (process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY) {
        const id = await createCredential('AWS', 'aws', {
            accessKeyId: process.env.AWS_ACCESS_KEY_ID,
            secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
            region: process.env.AWS_REGION || 'ap-northeast-2'
        });
        if (id) credentialMapping.aws = id;
    }

    // GCP (참고용 - JWT 방식 사용으로 실제로는 사용 안 함)
    const gcpKeyPath = '/credentials/gcp-key.json';
    if (fs.existsSync(gcpKeyPath)) {
        try {
            const gcpKey = JSON.parse(fs.readFileSync(gcpKeyPath, 'utf8'));
            await createCredential('Google API', 'googleApi', {
                email: gcpKey.client_email,
                privateKey: gcpKey.private_key,
                scope: 'https://www.googleapis.com/auth/cloud-platform'
            });
        } catch (e) {
            console.log('[init] Warning: Failed to parse GCP key file:', e.message);
        }
    }

    // Instagram
    if (process.env.INSTAGRAM_ACCESS_TOKEN) {
        await createCredential('Facebook Graph API', 'facebookGraphApi', {
            accessToken: process.env.INSTAGRAM_ACCESS_TOKEN
        });
    }

    // 3. 워크플로우 확인 및 credential 연결
    const workflow = await getWorkflowByName('Upvy AI Content Generator');
    if (workflow) {
        console.log(`[init] Found workflow (id: ${workflow.id}), linking credentials...`);
        await updateWorkflowCredentials(workflow.id, credentialMapping);
    } else {
        console.log('[init] Workflow not found, will be imported by CLI');
    }

    console.log('[init] ✓ Initialization completed');
}

main().catch(e => {
    console.error('[init] ✗ Init failed:', e.message);
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

        # 워크플로우 먼저 import (CLI로)
        if [ -f "$WORKFLOW_FILE" ] && [ ! -f "$MARKER_FILE" ]; then
            echo "[init] Importing workflow from $WORKFLOW_FILE..."
            if /usr/local/bin/n8n import:workflow --input="$WORKFLOW_FILE" 2>&1; then
                touch "$MARKER_FILE"
                echo "[init] ✓ Workflow imported"
            else
                echo "[init] Warning: Failed to import workflow"
            fi
        fi

        # Credential 생성 및 워크플로우 연결
        if [ ! -f "$CRED_MARKER_FILE" ]; then
            if run_init; then
                touch "$CRED_MARKER_FILE"
            fi
        else
            echo "[init] Credentials already configured"
        fi
    ) &
}

# 백그라운드 작업 시작
init_background

# 기본 n8n 엔트리포인트 실행
exec /docker-entrypoint.sh "$@"
