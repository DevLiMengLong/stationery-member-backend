# 文具店会员系统后端

Spring Boot 后端同时服务现有 Vue 管理端和商家微信小程序。两端共用 `stationery_member` 数据库、会员钱包和交易流水；小程序不再使用独立业务后端或独立数据库。

## 本地构建

需要 JDK 17、Maven、MySQL 8 和 Redis。数据库初始化脚本位于 `src/main/resources/db/init`，已存在数据库的增量脚本位于 `src/main/resources/db/migration`。

```bash
mvn test
mvn package
```

服务默认监听 `8090`。生产环境通过 Nginx 反向代理，Java 只监听 `127.0.0.1:8090`。

## 小程序登录配置

小程序使用 `wx.login` 的 code 换取 OpenID；AppSecret 只保存在后端环境变量中，禁止写入小程序源码、Git 或日志。

| 环境变量 | 用途 |
| --- | --- |
| `WECHAT_MINI_APP_ID` | 商家小程序 AppID |
| `WECHAT_MINI_APP_SECRET` | 商家小程序 AppSecret |

未配置上述变量时，Web 端账号密码登录和现有记账业务不受影响；小程序微信登录接口会返回配置未完成的业务错误。

## 支付模式

全局开关为 `STATIONERY_PAYMENT_MODE`：

- `BOOKKEEPING`：生产首发默认值，不需要微信支付商户号、证书或 API v3 密钥。小程序与 Vue 端继续使用原有会员充值、消费扣款、撤销和退款记账规则。
- `WECHAT_SERVICE_PROVIDER`：服务商模式。平台统一接入微信支付，各门店通过自己的特约商户号 `sub_mchid` 独立结算。只有平台配置完整、门店配置为 `REAL`、特约商户号存在且授权状态为 `ENABLED` 时，该门店才会开放真实支付；任何缺失、错误或待授权状态都会回退到 `BOOKKEEPING`。

服务商模式需要以下环境变量，文件路径必须指向服务器数据盘上的受限文件，不能放入发布包或 Git：

| 环境变量 | 用途 |
| --- | --- |
| `WECHAT_PAY_SP_APP_ID` | 服务商应用 AppID |
| `WECHAT_PAY_SP_MCHID` | 服务商商户号 |
| `WECHAT_PAY_MERCHANT_SERIAL_NUMBER` | 商户 API 证书序列号 |
| `WECHAT_PAY_PRIVATE_KEY_PATH` | 商户 API 私钥绝对路径 |
| `WECHAT_PAY_API_V3_KEY` | API v3 密钥 |
| `WECHAT_PAY_PUBLIC_KEY_ID` | 微信支付平台公钥 ID |
| `WECHAT_PAY_PUBLIC_KEY_PATH` | 微信支付平台公钥绝对路径 |
| `WECHAT_PAY_NOTIFY_URL` | 公网 HTTPS 支付回调，例如 `https://api.usepencils.com/api/payments/wechat/notify` |

每个门店的 `payment_mode`、`sub_mchid` 和 `authorization_status` 存储在 `store_wechat_pay_config`，不通过全局环境变量共享。当前尚无真实商户号时，不创建 `REAL + ENABLED` 配置即可正常使用全部记账业务。

真实支付首版仅用于会员充值。真实支付关联的充值流水，在后续商户入驻阶段完成微信退款接口和真实资金验收前，禁止使用现有仅修改本地账本的撤销/退款操作；记账流水保持原有处理方式。

## 生产环境变量

无密钥示例见 `ops/tencent/backend.env.example`。生产值保存于：

```text
/mnt/datadisk0/stationery-member/config/backend.env
```

该文件应仅允许运行用户读取。数据库文件、Redis 持久化、上传、发布包和备份也全部位于 `/mnt/datadisk0`；Java、MySQL、Redis、Nginx 软件包及 systemd/Nginx 配置仍由 Ubuntu 管理。
