[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" width="190" alt="ZhiHua Technology official logo">

# ZhiHua VisitFlow · Enterprise Visitor Reception and Onsite Register

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/).

Version 0.1.0 uses Java 21 / Spring Boot / Vue 3 / MySQL to connect employee registration, independent host confirmation, reception checks, badge issuance and actual entry/exit. Chinese and English interfaces serve administrative reception, employees, department managers and enterprise-software learners in a single-site private deployment.

**Source available for individual learning, technical research and non-commercial exchange.** Commercial use, paid deployment, client delivery, SaaS, resale and commercial training need prior written company authorization under the existing [LICENSE](LICENSE). This is not an OSI-approved open-source license. Third-party rights remain independent; source is provided as-is.

## Scenarios and actual workflow

Offices/service teams/factory sites can study registration-to-reception handover without connecting inventory, procurement, expenses, rental or meeting systems. No access-control device is integrated. History records what authenticated actors submitted; planned end never implies a visitor actually left.

| Actor | Implemented operations |
| --- | --- |
| Employee | Own draft creation/edits, independent host selection, submit/cancel, own designated host confirmations, associated records and export |
| Reception | Department visits/onsite register, physical verification, badge issue, entry/denial, actual badge return and checkout, department badge maintenance |
| Administrator | Full authorized visits/reception; accounts/roles/registered permissions, departments, navigation, visit-type dictionary and settings |

Registrant cannot be their own host. Administrator status does not bypass the designated host. Reception entry/exit needs dedicated permissions; employees cannot check themselves in.

```text
Draft → Submit → Pending host → Confirmed → Reception verification/badge issue → Onsite → Badge return/actual exit
                   ↓ Reject       ↓ Revoke             ↓ Deny entry
                Editable return   Cancelled            Denied
```

Only never-submitted own drafts can be deleted. Submitted history remains. Pending/confirmed visits expire at planned end, with a one-minute processing cycle. **Onsite records never check out automatically**; after planned end they remain onsite/overdue with the badge issued until actual handover is recorded.

## Implemented functions and limits

- Registration stores visitor name, organization, purpose, type, designated host and planned instants; search/status/own filters, database pagination and deterministic sorting.
- Host independently approves, rejects for editing or revokes. Approved personal/visit details freeze.
- Reception manually checks visitor/confirmation, selects a same-department enabled unused badge, records actual entry or denial, then records actual exit after confirming badge return.
- Badge codes/names/departments/enable state have versions. Concurrent issue of one badge has a single winner. An issued badge cannot disable; historical code/department freeze.
- Onsite list, personal workbench, visit totals, overdue onsite and completed reception minutes, scoped JSON/event snapshot export, immutable events and audit.
- User/role/department/navigation/permission/dictionary/settings administration with ALL/DEPARTMENT/ASSIGNED scopes and bilingual UI.
- BCrypt 12, same-origin sessions/CSRF, login failure limits, live disable/password invalidation, row locks, versions and request fingerprints.

A visit lasts at most twelve hours and may cross midnight, without recurrence. Asia/Shanghai governs display/rules; UTC persists. Registration window defaults to ninety days, configurable 1–180. A walk-in start can be up to thirty minutes in the past. Early entry defaults to thirty minutes, configurable 0–120 and frozen at submission. Entry is rejected at/after planned end.

Not implemented: public visitor self-registration, QR passage tokens, identity-card/photo capture, face recognition, identity-verification providers, blacklist screening, door opening, printer integration, SMS/email/WeCom notices, e-signing, repeated-visit merging, group booking, lost-badge handling, multisite, SSO, tenant isolation, AI or large-capacity certification. Manual reception checks are not electronic identity validation. A lost badge cannot be falsely marked returned; actual resolution must precede the supported checkout operation.

No demo mode or simulated external service exists; core flow needs no external API account. Deployment-owned HTTPS, domain, backup and monitoring are still required for an external installation.

## Actual running screens

Existing screenshots show actual isolated-test operation, using clearly labelled acceptance fixtures absent from normal initialization.

| Account login | Employee workbench |
| --- | --- |
| ![Login](docs/screenshots/login.jpg) | ![Own registration and host confirmations](docs/screenshots/workbench.jpg) |
| Visit search and registration | Reception badge catalog |
| ![Visit list](docs/screenshots/visits.jpg) | ![Badges](docs/screenshots/badges.jpg) |
| Authorized visit totals | Roles and data scopes |
| ![Statistics](docs/screenshots/dashboard.jpg) | ![Roles](docs/screenshots/roles.jpg) |
| Visit details and event history | Actual onsite register |
| ![Visit detail](docs/screenshots/detail.jpg) | ![Onsite visitors](docs/screenshots/onsite.jpg) |


## Environment, architecture and directories

Docker Engine/Desktop, Compose v2, Python 3, official dependency access and an unused loopback port. Separate source development requires Java **21**, Maven **3.9**, Node.js **24.19.0** and dedicated MySQL **8.4**. Pinned framework versions: Spring Boot **4.0.7**, Vue **3.5.40**, Vite **8.1.5**, Lucide **1.48.0**, MariaDB JDBC **3.5.10**, Nginx **1.29**.

Vue → same-origin Nginx → Spring Boot/Security/JPA → MySQL. Flyway V1/V2 migrates; JPA validates. UTC database instants display in fixed Shanghai time. Backend/frontend run non-root with only the local gateway mapped; the Java runtime image includes Maven build tools.

```text
backend/src/main/java/cn/zhuatech/visitflow/  Visits, authorization, reception and administration
backend/src/main/resources/db/migration/    V1 identity, V2 visitors and badges
backend/src/test/                            HTTP and time-rule tests
frontend/src/                               Bilingual business/admin views
frontend/public/brand/                     Official logo
scripts/                                    Private initialization, MySQL acceptance and release scan
docs/                                       Operation, deployment, API, architecture, security and screenshots
compose.yaml                                MySQL, backend, frontend and persistent volume
```

`visitor_visit`, `visitor_badge`, `visit_event` and `visit_command` persist owners/hosts/department snapshots, planned/actual times, badge identity, versions and actor/key fingerprints. Identity tables hold accounts, roles/permissions, departments, navigation, settings/dictionaries/audit. Foreign keys protect history. See [architecture](docs/architecture.md).

READ_COMMITTED writes lock actor, visit and then badge for issue/return. Different reception actors recheck onsite association while holding the badge lock; only one can issue. Badge changes use the same actor/badge order. Expiry locks its visit without clearing an onsite badge. Exact-key retries still check current scope/permission; changed content cannot reuse the key. Failed writes roll back atomically.

ALL reads authorized site records; DEPARTMENT includes own department and authored/hosted visits; ASSIGNED includes authored/hosted only. Reception additionally needs its permission/scope. Directories omit login names/passwords/visitor information; detail, events, snapshots, export and onsite lists are independently authorized.

## Install, configure and initialize

From a fresh checkout:

```sh
git clone https://github.com/zhuatech-han/zhuatech-visitflow.git
cd zhuatech-visitflow
python3 scripts/init-env.py
docker compose -p visitflow config --quiet
docker compose -p visitflow up -d --build --wait
```

Open `http://127.0.0.1:8103/`; health is `/actuator/health` through the same entry. First username is `admin`; read independent initial `ADMIN_PASSWORD` privately in ignored `.env`. The generator creates three strong secrets, mode 0600, without displaying or overwriting existing files. No public universal password exists. New initialization settings do not reset existing/restored accounts.

Only empty-database startup seeds main department, administrator/employee/reception roles, permissions, thirteen navigation entries, three visit types and parameters. It creates **no visitors or badges**. First create employees and reception, then same-department badges. Normal stop `docker compose -p visitflow down` retains data; `down -v` is only for explicitly disposable tests.

| Variable | Purpose |
| --- | --- |
| `DATABASE_PASSWORD` | Required independent application database password |
| `MYSQL_ROOT_PASSWORD` | Required independent MySQL administration password |
| `ADMIN_PASSWORD` | Empty-database administrator; minimum twelve upper/lowercase/digit characters, maximum seventy-two UTF-8 bytes |
| `WEB_PORT` / `BIND_ADDRESS` | 8103 / 127.0.0.1; choose unused port without stopping another project |
| `COOKIE_SECURE` | false for local HTTP, true for trusted HTTPS |

For host source development safely inject `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, matching `DATABASE_CATALOG` and `ADMIN_PASSWORD` for a dedicated database. These are direct-running overrides; adapting Compose needs corresponding environment mapping. Internal mysql is not a host-reachable endpoint and `.env` does not automatically load into Java.

```sh
mvn -f backend/pom.xml spotless:check test spring-boot:run
cd frontend
npm ci
npm run dev
```

Vite `http://127.0.0.1:5173/` proxies host backend 8080. Never connect acceptance scripts to a production database or put secrets in frontend files.

## Tests and independent acceptance

```sh
mvn -B -f backend/pom.xml spotless:check clean package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose -p visitflow-check config --quiet
docker compose -p visitflow-check build
docker compose -p visitflow-check up -d --wait
python3 scripts/release-check.py
git diff --check
```

Tests cover reception states, independent host, scopes, expiry, actual onsite retention, badge return, time boundaries, idempotency and concurrent single-badge issue. H2 MySQL-compatible HTTP integration does not replace actual MySQL. Docker Maven builds run tests without skipping them; source/runtime dependencies retain original notices.

Only on a fresh explicitly disposable loopback test project with matching private `.env`:

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8103 --run
python3 scripts/smoke.py --base http://127.0.0.1:8103 --verify
```

The script stores private synthetic credentials/state in ignored `.smoke-state.json`; use independent settings/output when prior QA exists. It tests independent host, manual verification/return confirmations, scoped privacy, badge concurrency, stale versions, retries, denial/revocation and export. Restart/restore must retain original accounts, onsite facts/issued badge, completed history and pending/normal expiry. Actual desktop/mobile/bilingual browser operations are separate gates, not proof of stress/security certification.

## Deployment, migration, backup and limits

External deployment needs trusted HTTPS/Secure cookies, least privilege, database certificate verification, restricted visitor/backup/export access, monitoring and dependency maintenance. The internal JDBC `sslMode=trust` does not verify CA identity; external MySQL requires `verify-full` and trusted CA. Same-origin sessions are thirty minutes, HttpOnly/SameSite Strict/CSRF/BCrypt 12; eight failed logins restrict attempts for five minutes, without distributed protection claims.

Back up MySQL consistently and restrict/encrypt configuration/business/account data outside Git. Independently restore into a separate project, unused port and fresh volume before application startup; check V1__identity.sql/V2__visitors.sql history, schema, original passwords, host/reception scopes, completed events and onsite/issued badge. Upgrade first in a restored copy; add higher SQL versions instead of changing applied migrations, and do not repair away discrepancies. See [deployment](docs/deployment.md).

Restart MySQL first and wait healthy; then restart backend/frontend and wait healthy. Compose restart does not reapply depends_on startup ordering. Do not globally prune other applications or destroy real volumes; only clean named disposable resources.

One backend, site and MySQL instance. API pages cap at 100, UI uses 20, catalogs cap at 10,000 and statistics reject above 10,000. Each minute processes at most 10,000 expired not-entered visits, without checking out onsite visitors. No multisite/distributed scheduling or high-volume assurance exists.

Only name/organization/purpose are collected, without requiring identity card, photos or phone. Visitor exports still need access control. The deployer decides notice/retention/removal rules; no one-click anonymization or submitted-record hard deletion exists. Backups/events/exports retain records under their applicable access policies.

## Troubleshooting, license and feedback

For empty startup check independent strong secrets/health/migrations. No host requires another enabled employee with read/approval rights. No badge requires same-department enabled/actually returned inventory. Entry requires host confirmation/time window/manual verification; checkout requires actual badge return and note. Refresh stale versions before deliberate retry. Planned end with onsite state requires actual checkout, not assumed departure. New `.env` passwords do not reset old accounts.

[Operations](docs/operations.md), [API](docs/api.md), [architecture](docs/architecture.md), [security](docs/security.md) and [release information](docs/releases.md) explain current behavior. Submit safe issues and verified small changes without real visitors, client records, credentials/private logs. Preserve existing own-source LICENSE and [Vue](docs/licenses/vue.txt)/[Lucide](docs/licenses/lucide.txt) notices; brand attribution does not change third-party rights. Report vulnerabilities privately. No identity/access-control or production-compliance guarantee is provided.

## Contact ZhiHua Technology

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**. Commercial authorization, customization, deployment and system integration:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
