# VisitFlow 架构与数据

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业授权、定制开发、部署与系统集成咨询微信：zhuatech / zhuatech2。

## 数据模型

Vue → 同源Nginx → Spring Boot → MySQL8.4。Flyway执行V1/V2，JPA validate，不依赖本机既有库。数据库和服务存UTC，页面及来访规则使用Asia/Shanghai。

visitor_visit保存登记人、被访人、被访部门快照、计划和实际时刻、状态、访客牌和版本。visitor_badge保存独立牌编号、部门、启停和版本。visit_event保存不可通过接口修改的事件及JSON快照。visit_command以actor/request_key唯一，绑定来访和载荷指纹。

身份基础为account、access_role、role_permission、permission、department、nav_menu、dictionary_entry、system_setting、audit_event，外键保护关联。完整DDL以backend/src/main/resources/db/migration为准。

## 并发顺序

写事务使用READ_COMMITTED，先锁当前操作者账号，再锁来访，发牌和收牌最后锁牌。相同操作者的重试串行化；不同前台发同一牌在牌锁内重新查询IN_SITE，只有一个成功。牌资料更新先锁操作者、再锁牌，并检查已发放关联。过期任务只锁单据，不碰在场牌；每个单据独立事务。

版本防止旧表单覆盖，操作者和请求键唯一防重复；摘要包括动作、来访ID和载荷。相同请求重试仍检查权限和数据范围，同键变内容拒绝。已提交业务及牌引用历史保留，删除只用于未引用牌和未提交草稿。

## 数据范围

ALL可查看所有授权来访；DEPARTMENT可看所属部门及本人登记／本人被访；ASSIGNED仅本人登记或本人被访。接待还要求reception及部门范围。岗位目录不含用户名、密码或访客信息；业务详情、快照、导出和在场名单均验权，其他人无权读取。

## 容量与任务

数据库分页来访每页最多100条；页面20条。主数据有界一万条，统计最多一万条，超过时拒绝。每分钟最多处理一万条到期未入场记录；在场记录不自动离场。单后端实例、单场所和单MySQL，未验证多实例调度或大规模容量。
