# ZhiYu-Backend 统一认证与授权接口规范 (API Specification)

本规范详细定义了 ZhiYu-Backend 的认证（Authentication）与授权（Authorization）服务的通信协议与接口细节，以便前端 App、Web 端以及其他微服务或第三方合作程序进行集成和调用。

---

## 1. 全局设计规范 (Global Conventions)

### 1.1 基础信息
* **服务基准路径 (Base URL)**: `/api/v1/auth`
* **协议与数据格式**: 采用 `HTTPS` 传输，请求与响应报文体统一为 `application/json;charset=UTF-8`。

### 1.2 统一响应报文格式 (Uniform Response)
所有接口在成功或失败时，均返回如下标准化 JSON 响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "requestId": "req_8471928a3f901c",
  "timestamp": 1716871200000
}
```

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `code` | Integer | 业务状态码。`0` 表示操作成功；非 `0` 表示业务异常，详见本规范中的“错误码矩阵”。 |
| `message` | String | 友好可读的错误说明或状态描述。系统已集成 i18n 多语言环境，会根据请求头自动适配语言。 |
| `data` | Object/Array | 业务返回的载荷实体，若当前接口无特定数据返回，则为 `null`。 |
| `requestId` | String | 本次请求的唯一追踪 ID（Trace ID），用于在系统日志中定位故障。 |
| `timestamp` | Long | 服务端响应的毫秒级时间戳。 |

### 1.3 鉴权控制流 (Authentication Flow)
本平台采用基于 **RSA-256 签名的 JWT 双 Token 校验机制**：
1. **Access Token (访问令牌)**: 
   * **有效期**: 900 秒（15分钟）。
   * **传递方式**: 调用受限资源接口时，必须在 HTTP Request Header 中携带：
     ```http
     Authorization: Bearer <accessToken>
     ```
2. **Refresh Token (刷新令牌)**:
   * **有效期**: 7 天。
   * **传递方式**: 仅用于在 Access Token 过期时请求 `/auth/refresh` 换取新令牌，通常存储于安全媒介中（如 App 的 Keychain 或 Web 端的 HttpOnly Cookie）。
   * **安全策略**: **一次性使用 (One-Time Use)**。旧的 Refresh Token 一旦使用即刻加入黑名单失效，可有效防御重放攻击（Replay Attack）。

---

## 2. 核心认证与登录接口明细

### 2.1 手机验证码发送接口 (Send SMS Verification Code)
向指定手机号发送 6 位数字验证码，用于短信免密登录或注册。

* **HTTP 方法**: `POST`
* **接口路径**: `/api/v1/auth/sms/send`
* **请求头**: `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "phone": "13800138000",
    "scene": "login"
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `phone` | 是 | String | "13800138000" | 符合中国大陆或国际规范的手机号。 |
  | `scene` | 是 | String | "login" | 验证码业务场景，可选值：`login` (登录), `register` (注册), `reset_pwd` (找回密码)。 |

* **响应示例 (成功)**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": null,
    "requestId": "sms_908f712ac82b",
    "timestamp": 1716871210000
  }
  ```

---

### 2.2 统一登录接口 (Password & SMS Login)
此端点整合了**密码登录**和**短信验证登录**两种核心机制，由 `grantType` 参数决定鉴权流。

* **HTTP 方法**: `POST`
* **接口路径**: `/api/v1/auth/login`
* **请求头**: `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "grantType": "password",
    "username": "testuser",
    "password": "MySecretPassword123",
    "phone": "13800138000",
    "smsCode": "186902",
    "privacyConsent": true,
    "captchaToken": "cap_87fac012ab",
    "captchaCode": "A3x9"
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `grantType` | 是 | String | "password" | 登录授权模式。可选值：`password` (密码模式), `sms_code` (短信验证码模式)。 |
  | `username` | 条件 | String | "testuser" | 用户名/邮箱/手机号。当 `grantType = "password"` 时必填。 |
  | `password` | 条件 | String | "MySecret..." | 加密或明文密码。当 `grantType = "password"` 时必填。 |
  | `phone` | 条件 | String | "13800138000" | 短信接收手机号。当 `grantType = "sms_code"` 时必填。 |
  | `smsCode` | 条件 | String | "186902" | 6 位数字验证码。当 `grantType = "sms_code"` 时必填。 |
  | `privacyConsent`| 是 | Boolean| `true` | 隐私政策勾选状态。必须上报 `true` 才能登录，否则直接拒绝，满足合规法案要求。 |
  | `captchaToken` | 否 | String | "cap_87fa..." | 验证码校验 Token。在连续登录失败 3 次以上系统强制开启滑动/图形验证码时必填。 |
  | `captchaCode`  | 否 | String | "A3x9" | 验证码输入字符，配合 `captchaToken` 验证。 |

* **响应示例 (成功)**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "accessToken": "eyJhbGciOiJSUzI1NiIs...",
      "refreshToken": "rf_9021a8c01ab8c",
      "expiresIn": 900,
      "tokenType": "Bearer",
      "totpRequired": false
    },
    "requestId": "log_a08912acb712",
    "timestamp": 1716871225000
  }
  ```

---

### 2.3 运营商一键登录 (Carrier SDK Auto-Login)
该端点用于 iOS/Android 原生 App 客户端集成运营商（移动/联通/电信）一键认证后，通过 SDK 拿到的令牌在后端无密完成手机号提取与免密登录。

* **HTTP 方法**: `POST`
* **接口路径**: `/api/v1/auth/carrier`
* **请求头**: `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "carrierToken": "carrier_token_example_123",
    "appKey": "app_key_ios_098",
    "privacyConsent": true
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `carrierToken` | 是 | String | "carrier_tok..."| 运营商 SDK 返回的本机号码验证加密 `accessToken`。 |
  | `appKey` | 是 | String | "app_key_ios..."| 注册在各大平台的移动应用 AppKey，便于后端识别证书解密。 |
  | `privacyConsent`| 是 | Boolean| `true` | 隐私政策同意状态。必须为 `true`。 |

* **响应示例 (成功)**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "accessToken": "eyJhbGciOiJSUzI1NiIs...",
      "refreshToken": "rf_1238af10adca",
      "expiresIn": 900,
      "tokenType": "Bearer",
      "isNewUser": false
    },
    "requestId": "car_09a871bc735b",
    "timestamp": 1716871230000
  }
  ```

---

### 2.4 游客跳过登录接口 (Guest / Anonymous Login)
签发带有受限 `LIMITED` 角色和低级 Scope 权限的短效凭证，允许未注册用户体验受保护的通用浏览接口。

* **HTTP 方法**: `POST`
* **接口路径**: `/api/v1/auth/guest`
* **请求头**: `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "deviceId": "device_fingerprint_example_456",
    "privacyConsent": true
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `deviceId` | 否 | String | "device_fing..."| 客户端生成的唯一设备标识，上报可使同一个游客重启后复用统一虚拟账户。 |
  | `privacyConsent`| 否 | Boolean| `true` | 隐私协议同意状态。部分地区游客体验同样需上报。 |

* **响应示例 (成功)**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "accessToken": "eyJhbGciOiJSUzI1NiIs...", // 该 Token 包含 LIMITED 角色标识
      "expiresIn": 900,
      "tokenType": "Bearer"
    },
    "requestId": "gst_18fa0982a17f",
    "timestamp": 1716871235000
  }
  ```

---

### 2.5 第三方 OAuth 登录接口 (WeChat / Apple / Google / GitHub)
集成第三方社交或开放平台登录，支持微信开放平台、Apple 账户、Google 以及 GitHub 的认证回调机制。

* **HTTP 方法**: `POST`
* **接口路径**:
  * 微信授权登录: `/api/v1/auth/oauth/wechat`
  * Apple 鉴权登录: `/api/v1/auth/oauth/apple`
  * Google 授权登录: `/api/v1/auth/oauth/google`
  * GitHub 授权登录: `/api/v1/auth/oauth/github`
* **请求头**: `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "code": "081xYz0w3QnP1L1t4J0w3nZyDG1xYz0F",
    "state": "csrf_state_9801a",
    "idToken": "apple_id_token_jwt"
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `code` | 是 | String | "081xYz..." | 第三方授权回调流程中所获取的授权码 (Authorization Code)。 |
  | `state` | 否 | String | "csrf_st..." | 用于防止 CSRF 攻击的安全随机态（与三方平台下发的保持一致）。 |
  | `idToken` | 条件 | String | "eyJhbGci..." | 部分单点登录平台（如 Apple / Google 登录）使用的第三方加密令牌。 |

* **响应示例 (成功)**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "accessToken": "eyJhbGciOiJSUzI1NiIs...",
      "refreshToken": "rf_oauth_09fac",
      "expiresIn": 900,
      "tokenType": "Bearer",
      "isNewUser": true                         // 若此第三方社交账号为首次登录自动注册，返回 true
    },
    "requestId": "oau_71fa9021b01c",
    "timestamp": 1716871242000
  }
  ```

---

### 2.6 无感会话刷新接口 (Silent Token Refresh)
使用长效且一次性的 `refreshToken` 在后台无感知地置换新的一组 JWT，维持用户正常活跃会话。

* **HTTP 方法**: `POST`
* **接口路径**: `/api/v1/auth/refresh`
* **请求头**: `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "refreshToken": "rf_9021a8c01ab8c"
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `refreshToken`| 是 | String | "rf_9021..." | 保存在客户端长效存储中的刷新令牌。 |

* **响应示例 (成功)**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "accessToken": "eyJhbGciOiJSUzI1TmV3...", // 崭新的访问令牌
      "refreshToken": "rf_new_987acb0121",      // 崭新的长效刷新令牌，旧的即刻在缓存中列入黑名单作废
      "expiresIn": 900,
      "tokenType": "Bearer"
    },
    "requestId": "ref_81ca902ab912",
    "timestamp": 1716871250000
  }
  ```

---

### 2.7 会话安全登出接口 (Logout / Invalidate Sessions)
安全注销当前用户的在线状态。注销后，用户的 AccessToken 将被塞入全局 Redis 黑名单拒绝通信，对应的 RefreshToken 自动被清除。

* **HTTP 方法**: `POST`
* **接口路径**: `/api/v1/auth/logout`
* **请求头**: 
  * `Authorization: Bearer <accessToken>` (必须)
  * `Content-Type: application/json`
* **请求体参数**:
  ```json
  {
    "refreshToken": "rf_9021a8c01ab8c"
  }
  ```
  | 参数名 | 必选 | 类型 | 示例值 | 说明 |
  | :--- | :---: | :--- | :--- | :--- |
  | `refreshToken`| 否 | String | "rf_9021..." | 可选，客户端同时上报其对应的 RefreshToken，以确保双重失效。 |

* **响应示例**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": null,
    "requestId": "out_08fa671ab92c",
    "timestamp": 1716871260000
  }
  ```

---

## 3. 系统错误码矩阵 (Global Error Codes)

当响应中的 `code` 字段不等于 `0` 时，系统进入异常分支。以下是认证模块相关核心错误码：

| 错误码 (code) | HTTP 状态码 | 中文说明 | 常用场景说明 |
| :--- | :---: | :--- | :--- |
| `40101` | `200` | Token 已过期 | 访问令牌超时，应发起 `refresh` 请求。 |
| `40102` | `200` | Token 验证无效/篡改 | 非法伪造 of Token 或被手动撤销/登出拉黑的 Token。 |
| `40103` | `200` | Token 重放攻击被拦截 | Refresh Token 发生二次使用尝试，系统自动吊销其全设备会话。 |
| `40105` | `200` | 用户名或密码错误 | 密码登录验证失败。 |
| `40106` | `200` | 账号已被临时锁定 | 密码连续错误达到 5 次，临时冻结，请等候冷却或申诉。 |
| `40107` | `200` | 账号已被禁用 | 账号因违规或管理员操作，处于封禁状态。 |
| `40109` | `200` | 验证码错误 | 注册或登录的数字验证码校验失败。 |
| `40111` | `200` | 需要安全人机验证码 | 账号行为异常，要求在参数中上报人机滑块凭证。 |
| `40119` | `200` | 必须勾选同意隐私政策 | 客户端没有在登录请求中携带 `"privacyConsent": true`。 |
| `41502` | `200` | 第三方 OAuth 授权码失效 | 回调的 code 已失效或过期。 |
| `41603` | `200` | 不支持的第三方平台 | 暂未授权配置的登录提供商（如非法渠道来源）。 |
| `41604` | `200` | 第三方网络暂不可用 | 后端访问微信、Google 等三方服务器超时或失败。 |
| `42903` | `200` | 请求频率超限 | 60 秒内多次调用发送验证码接口，被频率限制拦截。 |

---

## 4. 客户端开发接入建议 (Client Developer Best Practices)

为保障通信性能与安全性，强烈建议开发客户端的人员参考以下两套高阶控制策略：

### 4.1 自动拦截静默无感刷新 (Auto-Refresh Interceptor)
使用 `axios`、`fetch` 等网络库时，可以设计统一的**响应拦截器 (Response Interceptor)**。伪代码逻辑如下：

```javascript
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

axios.interceptors.response.use(
  (response) => {
    // 若后端响应带有业务级的 40101 (AccessToken 过期)
    if (response.data && response.data.code === 40101) {
      const originalRequest = response.config;
      
      if (!isRefreshing) {
        isRefreshing = true;
        
        // 发起静默刷新
        return new Promise((resolve, reject) => {
          refreshTokenRequest(getStoredRefreshToken())
            .then(res => {
              if (res.code === 0) {
                // 成功置换新令牌，更新本地存储
                saveTokens(res.data.accessToken, res.data.refreshToken);
                originalRequest.headers['Authorization'] = 'Bearer ' + res.data.accessToken;
                processQueue(null, res.data.accessToken);
                resolve(axios(originalRequest));
              } else {
                // 刷新失败（Refresh Token 也过期了），清除登录态，跳转到登录页
                handleUserLogout();
                reject(new Error("Refresh expired"));
              }
            })
            .catch(err => {
              processQueue(err, null);
              handleUserLogout();
              reject(err);
            })
            .finally(() => {
              isRefreshing = false;
            });
        });
      } else {
        // 当前已经在刷新状态中，把其余并发请求挂起并放入队列
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
        .then(token => {
          originalRequest.headers['Authorization'] = 'Bearer ' + token;
          return axios(originalRequest);
        })
        .catch(err => Promise.reject(err));
      }
    }
    return response;
  }
);
```

### 4.2 设备安全存储 (Secure Token Storage)
* **移动端 App (iOS / Android)**: 绝对禁止将 `refreshToken` 与 `accessToken` 作为普通的明文 `SharedPreferences` 或 `UserDefaults` 存储。应必须存入操作系统的**钥匙串 (iOS Keychain)** 或使用硬件保护库（如 Android Keystore / EncryptedSharedPreferences）。
* **Web 浏览器**: 建议在页面关闭即失的内存变量中存储 `accessToken`，而把长效的 `refreshToken` 写入支持 `HttpOnly` 以及 `Secure` 和 `SameSite=Lax` 属性的 Cookie 中，或者保存在独立的 WebWorker 隔离区，最大化防御跨站脚本攻击 (XSS)。
