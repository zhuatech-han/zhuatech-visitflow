# VisitFlow 接口

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业授权、定制开发、部署与系统集成咨询微信：zhuatech / zhuatech2。

## 身份和管理

所有接口位于/api。GET /auth/csrf可匿名引导，POST /auth/login使用账号密码建立同源会话；POST /auth/logout，GET /auth/me，POST /auth/password。写请求使用CSRF header，业务代码再次查询账号和角色。健康为/actuator/health。

GET /options提供非敏感被访岗位目录、部门、类型和参数。GET/POST /admin/{type}、PUT/DELETE /admin/{type}/{id}的type限users、roles、departments、menus、permissions、dictionaries、settings，要求admin。账号响应不含passwordHash，历史引用由外键保护。

## 来访

GET /visits参数search、status、page、size、sort=time/newest/name、mine、onsite；onsite=true额外要求reception且只返回IN_SITE。POST /visits和PUT /visits/{id}输入hostId、visitorName、organization、purpose、category、startsAt、endsAt、requestKey，更新加version。服务端决定ownerId、departmentId和status。

GET /visits/{id}返回visit、被访人和登记人显示名称、badgeCode、events。DELETE带version仅删除未送审草稿。GET /visits/{id}/report.json额外要求export。

POST /visits/{id}/commands/{action}输入version、requestKey、note；入场另输入badgeId、identityConfirmed=true，离场另输入badgeReturned=true及必填意见。action限submit、approve、reject、cancel、revoke、deny、check-in、check-out。实际入场和离场时间由服务端生成。

## 访客牌与统计

GET /badges返回badge及inUse，只供获准岗位。POST/PUT输入code、name、departmentId、enabled和更新version；DELETE带version且必须无来访历史引用。管理要求badge.manage，发放要求reception。

GET /workbench本人活动来访和指定被访待确认；GET /dashboard要求visit.read及dashboard，GET /audit要求audit并限制范围。错误返回code及HTTP 400/401/403/404/409；版本、状态、牌在用、权限和幂等错误不可通过客户端强制覆盖。
