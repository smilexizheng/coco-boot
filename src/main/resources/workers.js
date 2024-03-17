
addEventListener('fetch', event => {
    event.respondWith(handleRequest(event.request))
})

addEventListener('scheduled', event => {
    event.waitUntil(doSomeTaskOnASchedule());
});

async function doSomeTaskOnASchedule() {
    await handleCheckReset();
    await handleChecklist();
}

function generateUUID() {
    const array = new Uint8Array(16);
    crypto.getRandomValues(array);
    array[6] = (array[6] & 0x0f) | 0x40; // 设置版本为4
    array[8] = (array[8] & 0x3f) | 0x80; // 设置变体为1
    const hex = [...array].map(b => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}${hex.slice(8, 12)}${hex.slice(12, 16)}${hex.slice(16, 20)}${hex.slice(20)}`;
}

async function handleAuth(request) {
    const redirectUri = encodeURIComponent(REDIRECT_URI);
    // 生成随机的 state 值
    const state = generateUUID(); // 请实现此函数
    // 可以选择存储 state 值到 KV 中，以便稍后验证
    await OAUTH_TOKENS.put(`state_${state}`, "true", {expirationTtl: 300}); // 存活时间设置为 5 分钟
    const authUrl = `${AUTHORIZATION_ENDPOINT}?client_id=${CLIENT_ID}&state=${state}&redirect_uri=${redirectUri}&response_type=code&scope=read`;
    return Response.redirect(authUrl, 302);
}

async function handleCallback(request) {
    const url = new URL(request.url);
    const code = url.searchParams.get("code");
    const state = url.searchParams.get("state");
    // 验证 state 值
    const storedState = await OAUTH_TOKENS.get(`state_${state}`);
    if (!storedState) {
        // 如果 state 不存在或不匹配，应当拒绝请求
        return new Response("State value did not match", {status: 403});
    }
    await OAUTH_TOKENS.delete(`state_${state}`);
    const auth = 'Basic ' + btoa(CLIENT_ID + ':' + CLIENT_SECRET);
    // 构造请求 body
    const body = new URLSearchParams({
        'grant_type': 'authorization_code',
        'code': code,
        'redirect_uri': REDIRECT_URI
    });
    let data;
    try {
        const response = await fetch(TOKEN_ENDPOINT, {
            method: 'POST',
            headers: {
                'Authorization': auth,
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: body
        });
        // 解析响应体为 JSON
        data = await response.json();
    } catch (error) {
        return new Response('Internal Server Error', { status: 500 });
    }
    const accessToken = data.access_token;
    if (accessToken === null || accessToken === undefined) {
        return new Response('Error fetching token', { status: 500 })
    }
    // 使用 access_token 获取用户信息
    const userInfoResponse = await fetch(USER_ENDPOINT, {
        method: "GET",
        headers: {
            "Authorization": `Bearer ${accessToken}`
        }
    });
    const userInfo = await userInfoResponse.json();
    // 假设 userInfo 包含了 userId 字段
    const userId = userInfo.id;

    // 将 accessToken 存储到 KV 中，以 userId 作为键
    await OAUTH_TOKENS.put(`linuxdoAccessToken_${userId}`, accessToken, { expirationTtl: data.expires_in });
    await OAUTH_TOKENS.put(`linuxdoRefreshToken_${userId}`, data.refresh_token, { expirationTtl: data.expires_in + 10000 });

    const oldToken = await USER_DATA.get(`cctoken_${userId}`);
    if (oldToken !== undefined || oldToken !== null) {
        await OAUTH_TOKENS.delete(`cctokenUser_${oldToken}`)
    }

    const token = generateUUID()
    await OAUTH_TOKENS.put(`cctokenUser_${token}`, userId);
    await USER_DATA.put(`cctoken_${userId}`, token);
    await USER_DATA.put(`user_${userId}`, JSON.stringify(userInfo));
    return new Response(JSON.stringify({message: 'Token Get Success', data: token}), {status: 200});
}

async function refreshToken(userId) {
    const token = await OAUTH_TOKENS.get(`linuxdoRefreshToken_${userId}`);

    // 构造请求的 body
    const body = new URLSearchParams();
    body.append('grant_type', 'refresh_token');
    body.append('refresh_token', token.substring(token.indexOf(',') + 1));
    body.append('client_id', CLIENT_ID);
    body.append('client_secret', CLIENT_SECRET);

    try {
        const response = await fetch(TOKEN_ENDPOINT, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: body
        });

        // 解析响应体为 JSON
        const data = await response.json();

        if (response.ok) {
            // 存储新的访问令牌和刷新令牌（如果提供）
            await OAUTH_TOKENS.put(`linuxdoAccessToken_${userId}`, data.access_token, { expirationTtl: data.expires_in });
            if (data.refresh_token) {
                await OAUTH_TOKENS.put(`linuxdoRefreshToken_${userId}`, data.refresh_token, { expirationTtl: data.expires_in + 10000 });
            }

            return data.access_token; // 返回新的访问令牌
        } else {
            return null;
        }
    } catch (error) {
        return null;
    }
}


let CLIENT_ID = '';
let CLIENT_SECRET = '';
let REDIRECT_URI = '';
let AUTHORIZATION_ENDPOINT = '';
let TOKEN_ENDPOINT = '';
let USER_ENDPOINT = '';
let FREQUENCY_TIME = 1;
let FREQUENCY_DEGREE = 8;

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'OPTIONS,POST,GET',
    'Access-Control-Allow-Headers': '*',
};
const KV = cocopilot;
const OAUTH_TOKENS = oauth_tokens;
const USER_DATA = user_data;

async function handleRequest(request) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') return new Response(null, { headers: corsHeaders });
    if (url.pathname === '/token' && request.method === 'GET') {
        return await handleAuth(request);
    } else if (url.pathname === '/callback' && request.method === 'GET') {
        return await handleCallback(request)
    } else if (url.pathname === '/upload' && request.method === 'POST') {
        return await handleUpload(request);
    } else if (url.pathname === '/list' && request.method === 'GET') {
        return await handleList();
    } else if (url.pathname.startsWith('/v1')) {
        const auth = request.headers.get('Authorization');
        if (!auth.startsWith('Bearer ')) {
            return new Response('Invalid Authorization', {status: 405});
        } else {
            const token = auth.substring('Bearer '.length);
            const userId = await OAUTH_TOKENS.get(`cctokenUser_${token}`);
            if (userId === undefined || userId === null) {
                return new Response('token does not exist', {status: 405});
            } else {
                const at = await OAUTH_TOKENS.get(`linuxdoAccessToken_${userId}`);
                const rt = await OAUTH_TOKENS.get(`linuxdoRefreshToken_${userId}`);
                if (rt === undefined || rt === null) {
                    return new Response('Invalid Token', {status: 405});
                } else if (at === undefined || at === null) {
                    const rat = await refreshToken(userId);
                    if (rat === null) {
                        return new Response('Invalid Token', {status: 405});
                    }
                }
                const response = await handleProxy(request, url);
                const newHeaders = new Headers(response.headers);
                for (let key in corsHeaders) {
                    newHeaders.set(key, corsHeaders[key]);
                }
                return new Response(response.body, {
                    status: response.status,
                    headers: newHeaders
                });
            }
        }
    } else {
        return new Response('Invalid request method or path', {status: 405});
    }
}


async function handleUpload(request) {
    const data = await request.json();
    for (let key in data) {
        if (!key.startsWith('gh') || !data[key].startsWith('gh')) {
            return new Response(JSON.stringify({message: 'Key and value must start with "copilot secret"', data: data}), {status: 400});
        }
        const headers = {
            'Access-Control-Allow-Origin': '*',
            'Host': 'api.github.com',
            'authorization': `token ${key}`,
            'Editor-Version': 'vscode/1.85.2',
            'Editor-Plugin-Version': 'copilot-chat/0.11.1',
            'User-Agent': 'GitHubCopilotChat/0.11.1',
            'Accept': '*/*',
            'Accept-Encoding': 'gzip, deflate, br'
        }

        const upload_response = await fetch('https://api.github.com/copilot_internal/v2/token', { headers });
        const upload_responseBody = await upload_response.json();
        if (upload_response.status === 200 && upload_responseBody.token) {
            const value = {
                ghu: key,
                time: new Date().getTime(),
                rate: 0, // set rate to 0
                daily_count: 0, // initialize count to 0
                total_count: 0, // initialize count to 0
                uploadTime: new Date().toISOString(), // add upload time
                alive: true, // add alive field
                timestamps: []
            };
            await KV.put(key, JSON.stringify(value));
        }
    }
    return new Response(JSON.stringify({message: 'ghu upload Success', data: data}), {headers: corsHeaders, status: 200});
}

async function handleProxy(request, url) {
    let keys = await KV.list();
    if (keys.keys.length === 0) {
        return new Response(JSON.stringify({message: 'No keys'}), {status: 200});
    }
    // 获取rate为0且count小于40000且alive为true的keys
    keys = keys.keys;
    const values = await Promise.all(keys.map(key => KV.get(key.name)));

    let filterkeys = keys.map((key, index) => JSON.parse(values[index]))
        .filter(value => !value.rate && value.daily_count < 40000 && value.alive) // filter out keys with rate, count >= 40000 or not alive
        .sort((a, b) => (a.daily_count || 0) - (b.daily_count || 0));

    const now = Date.now();
    for (let ghu_value of filterkeys) {
        // 使用||运算符来处理timestamps可能为undefined或null的情况
        const filteredTimestamps = ghu_value.timestamps.filter(timestamp => now - timestamp < (FREQUENCY_TIME * 1000));

        if (filteredTimestamps.length < FREQUENCY_DEGREE) {
            // 此ghu符合频率限制，可以使用
            filteredTimestamps.push(now);
            ghu_value.timestamps = filteredTimestamps;

            const requestBody = await request.json();
            const auth = `Bearer ${ghu_value.ghu}`;

            const response = await fetch(`https://proxy.cocopilot.org${url.pathname}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': auth
                },
                body: JSON.stringify(requestBody)
            });

            // increment count after the request
            ghu_value.daily_count += 1;
            ghu_value.total_count += 1;
            await KV.put(ghu_value.ghu, JSON.stringify(ghu_value)); // update KV


            if (response.status === 429) {
                const retryAfter = response.headers.get('x-ratelimit-user-retry-after');
                console.log(retryAfter)
                if (retryAfter) {
                    ghu_value = JSON.parse(ghu_value); // parse ghu_value into an object
                    ghu_value.rate = retryAfter;
                    ghu_value.time = new Date().getTime(); // update timestamp
                    console.log(`写入限速的limit: ${ghu_value.name}`)
                    console.log(`写入限速的limit: ${ghu_value}`)
                    await KV.put(ghu_value.name, JSON.stringify(ghu_value));
                }
            }

            return response;
        }
        // 如果当前ghu超过频率限制，继续检查下一个ghu
    }

    // 如果所有ghu都超过频率限制
    return new Response(JSON.stringify({message: 'Rate limit exceeded for all keys'}), {status: 429});
}

async function handleList() {
    const keys = await KV.list();
    let runningCount = 0;
    let blockedCount = 0;
    let deadCount = 0;
    let aliveData = [];
    let deadData = [];

    const values = await Promise.all(keys.keys.map(key => KV.get(key.name)));

    for (let value of values) {
        let ghu_value = JSON.parse(value);

        if (ghu_value.rate === 0) {
            runningCount++;
        } else if (ghu_value.rate === -1) {
            deadCount++;
        } else {
            blockedCount++;
        }

        let data = {
            ghu: ghu_value.ghu.substring(0, 15), // get the first 6 characters of ghu
            daily_count: ghu_value.daily_count,
            total_count: ghu_value.total_count
        };

        if (ghu_value.rate === -1) {
            deadData.push(data);
        } else {
            aliveData.push(data);
        }
    }

    return new Response(JSON.stringify({total: keys.keys.length, running: runningCount, blocked: blockedCount, dead: deadCount, alive_data: aliveData, dead_data: deadData}), {status: 200});
}

async function handleChecklist() {
    const keys = await KV.list();
    if (keys.keys.length === 0) {
        return new Response(JSON.stringify({message: 'No keys in KV'}), {status: 200});
    }

    // auto delete kv
    for (let key of keys.keys) {
        const ghu = key.name;
        const headers = {
            'Host': 'api.github.com',
            'authorization': `token ${ghu}`,
            'Editor-Version': 'vscode/1.85.2',
            'Editor-Plugin-Version': 'copilot-chat/0.11.1',
            'User-Agent': 'GitHubCopilotChat/0.11.1',
            'Accept': '*/*',
            'Accept-Encoding': 'gzip, deflate, br'
        }

        const response = await fetch('https://api.github.com/copilot_internal/v2/token', { headers });
        const responseData = await response.json();

        if (response.status !== 200 || !responseData.token) {
            let ghu_value = await KV.get(key.name);
            ghu_value = JSON.parse(ghu_value);
            ghu_value.alive = false; // set alive to false
            ghu_value.rate = -1; // set rate to -1
            await KV.put(key.name, JSON.stringify(ghu_value)); // update KV
            // await KV.delete(key.name);
        }
    }
}

async function handleCheckReset() {
    const keys = await KV.list();
    if (keys.keys.length === 0) {
        return new Response(JSON.stringify({message: 'No keys in KV'}), {status: 200});
    }

    // auto reset rate
    const currentTime = new Date();
    const currentDay = currentTime.getDate();

    for (let key of keys.keys) {
        let ghu_value = await KV.get(key.name);
        ghu_value = JSON.parse(ghu_value);

        const ghuTime = new Date(ghu_value.time);
        const ghuDay = ghuTime.getDate();

        // If it's a new day, reset daily_count
        if (ghuDay !== currentDay) {
            ghu_value.daily_count = 0;
        }

        // Convert rate from seconds to milliseconds
        const rateInMilliseconds = ghu_value.rate * 1000;

        // If currentTime is greater than rateInMilliseconds + ghu_value.time, reset rate
        if (currentTime.getTime() >= rateInMilliseconds + ghu_value.time) {
            ghu_value.time = currentTime.getTime();
            ghu_value.rate = 0;
        }

        console.log(`ghu rate ${key.name}`);
        await KV.put(key.name, JSON.stringify(ghu_value));
    }
}