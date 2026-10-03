// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化员工与接待岗位，不创建访客或访客牌。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${visitflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 空库创建角色、导航与管理员；已有密码和业务不被重写。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    var names =
        Map.of(
            "visit.read",
            "查看授权来访",
            "visit.write",
            "登记本人来访",
            "visit.approve",
            "确认被访来访",
            "reception",
            "入场与离场接待",
            "badge.manage",
            "管理部门访客牌",
            "dashboard",
            "来访统计",
            "export",
            "导出授权记录",
            "audit",
            "操作审计",
            "admin",
            "系统管理");
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role(
        "员工",
        "ASSIGNED",
        Set.of("visit.read", "visit.write", "visit.approve", "dashboard", "export"));
    role(
        "前台",
        "DEPARTMENT",
        Set.of("visit.read", "reception", "badge.manage", "dashboard", "export", "audit"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"workbench", "我的接待", "My reception", "visit.read"},
      {"visits", "来访登记", "Visits", "visit.read"},
      {"onsite", "在场名单", "Onsite register", "reception"},
      {"badges", "访客牌", "Visitor badges", "badge.manage"},
      {"dashboard", "来访统计", "Visit statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "来访类型", "Visit types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of(
            "timezone",
            "Asia/Shanghai",
            "companyName",
            "知华访客接待",
            "visitHorizonDays",
            "90",
            "earlyArrivalMinutes",
            "30")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] kinds = {
      {"BUSINESS", "商务交流", "Business"}, {"SERVICE", "现场服务", "Service"}, {"OTHER", "其他来访", "Other"}
    };
    for (var c : kinds) {
      var e = new DictionaryEntry();
      e.type = "visit";
      e.code = c[0];
      e.name = c[1];
      e.nameEn = c[2];
      db.save(e);
    }
  }

  private void role(String name, String scope, Set<String> perms) {
    var a = new AccessRole();
    a.name = name;
    a.scope = scope;
    a.permissions = new HashSet<>(perms);
    db.save(a);
  }
}
