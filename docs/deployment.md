# VisitFlow 部署与升级

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业授权、定制开发、部署与系统集成咨询微信：zhuatech / zhuatech2。

## 启动

需要Docker Compose v2和Python3。执行python3 scripts/init-env.py，再docker compose -p visitflow config --quiet和docker compose -p visitflow up -d --build --wait。默认127.0.0.1:8103；被占用时改WEB_PORT，不停其他服务。MySQL和后端仅在内部网络。首次镜像构建会运行全部测试。

私有.env包含三项独立密码，脚本拒绝覆盖并设置0600。管理员admin的初始密码仅作用空库。数据库卷保存业务；正常停止down保留卷，仅可丢弃验收环境使用down -v。应用运行镜像以非root运行，后端沿用官方Maven Java21镜像，体积包含构建工具。

## 外部环境

部署方配置HTTPS网关、COOKIE_SECURE=true、数据库证书验证、密码管理和访问限制。当前内部MariaDB JDBC sslMode=trust没有CA身份核验，正式环境按驱动配置verify-full和CA。限制导出、备份及日志访问，访客资料不应公网匿名可见。

## 备份及迁移

升级前停止写入并使用数据库原生工具制作逻辑备份，安全保存.env和镜像版本；密码通过安全配置提供，不写进源码、报告或命令输出。备份独立恢复到测试数据库验证V1/V2、表结构、登录和记录再升级。

只新增递增版本迁移，禁止修改已执行SQL。启动自动迁移，JPA校验结构。失败读取迁移和后端日志，恢复可靠备份及旧镜像，不使用flyway repair绕过实际差异。完成后核验健康、登录、接待确认、入场、收牌离场和权限拒绝。

## 验收及清理

专用visitflow-check需全新数据库卷。smoke.py --run会生成明确验收测试账号和来访记录，不能用于真实业务库；重启后--verify检查实际在场和历史保留。结束只清理本次visitflow-check容器、网络和卷，不进行全局prune。正式使用的卷不可使用验收清理命令。

## 有序重启

数据库维护重启时先完成 `docker compose -p visitflow restart mysql` 和 `docker compose -p visitflow up -d --wait mysql`，再执行 `docker compose -p visitflow restart backend frontend` 及 `docker compose -p visitflow up -d --wait`。Compose 的 restart 不重新执行 depends_on 健康排序；同时重启全部服务会造成数据库短暂未就绪，应用依靠重启策略恢复。有序重启后核对健康、迁移和登录。
