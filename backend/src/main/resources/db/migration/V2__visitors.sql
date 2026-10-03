-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
CREATE TABLE visitor_badge (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 version bigint NOT NULL,
 code varchar(60) NOT NULL UNIQUE,
 name varchar(120) NOT NULL,
 department_id bigint NOT NULL,
 enabled boolean NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id)
);
CREATE TABLE visitor_visit (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 version bigint NOT NULL,
 owner_id bigint NOT NULL,
 host_id bigint NOT NULL,
 department_id bigint NOT NULL,
 visitor_name varchar(120) NOT NULL,
 organization varchar(200) NOT NULL,
 purpose varchar(2000) NOT NULL,
 category varchar(60) NOT NULL,
 status varchar(20) NOT NULL,
 starts_at timestamp(6) NOT NULL,
 ends_at timestamp(6) NOT NULL,
 checked_in_at timestamp(6),
 checked_out_at timestamp(6),
 badge_id bigint,
 submitted boolean NOT NULL,
 early_minutes int NOT NULL,
 created_at timestamp(6) NOT NULL,
 updated_at timestamp(6) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES account(id),
 FOREIGN KEY(host_id) REFERENCES account(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(badge_id) REFERENCES visitor_badge(id),
 CHECK(ends_at > starts_at),
 CHECK(early_minutes >= 0 AND early_minutes <= 120)
);
CREATE TABLE visit_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 visit_id bigint NOT NULL,
 actor varchar(60) NOT NULL,
 action varchar(40) NOT NULL,
 note varchar(1000) NOT NULL,
 snapshot text NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(visit_id) REFERENCES visitor_visit(id)
);
CREATE TABLE visit_command (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 visit_id bigint NOT NULL,
 actor varchar(60) NOT NULL,
 request_key varchar(80) NOT NULL,
 fingerprint varchar(64) NOT NULL,
 FOREIGN KEY(visit_id) REFERENCES visitor_visit(id),
 UNIQUE(actor, request_key)
);
CREATE INDEX ix_visit_scope ON visitor_visit(department_id,status,starts_at);
CREATE INDEX ix_visit_host ON visitor_visit(host_id,status);
CREATE INDEX ix_visit_owner ON visitor_visit(owner_id,created_at);
CREATE INDEX ix_visit_badge ON visitor_visit(badge_id,status);
CREATE INDEX ix_visit_event ON visit_event(visit_id,id);
