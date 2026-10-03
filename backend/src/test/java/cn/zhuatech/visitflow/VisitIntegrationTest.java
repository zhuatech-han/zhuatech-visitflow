// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 会话与真实接口验收；可控时钟验证爽约与自动结束，不向运行服务提供调时接口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(VisitIntegrationTest.TimeConfig.class)
class VisitIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("visitflow.admin-password", () -> password);
    r.add("visitflow.sweep-millis", () -> 86400000);
  }

  /** 测试专用可控时钟，生产使用系统 UTC 时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    Instant value = Instant.parse("2026-10-05T01:45:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  /** 测试环境时钟绑定。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  @Autowired VisitService service;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, requester, host, desk, stranger;
  long dept, hostId, ownerId, deskId, staffRole, badge, id;
  JsonNode v;
  String suffix, ownerName, hostName, deskName;
  final Instant start = Instant.parse("2026-10-05T02:00:00Z"), end = start.plusSeconds(3600);

  @BeforeEach
  void setup() throws Exception {
    clock.value = start.minusSeconds(900);
    admin = login("admin", password);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "验收测试接待-" + suffix))
            .path("id")
            .asLong();
    long deskRole = 0;
    for (var a : ok(admin, "GET", "/admin/roles", null)) {
      if (a.path("name").asString().equals("员工")) staffRole = a.path("id").asLong();
      if (a.path("name").asString().equals("前台")) deskRole = a.path("id").asLong();
    }
    ownerName = "requester-" + suffix;
    hostName = "host-" + suffix;
    deskName = "desk-" + suffix;
    ownerId = user(ownerName, staffRole, dept);
    hostId = user(hostName, staffRole, dept);
    deskId = user(deskName, deskRole, dept);
    user("stranger-" + suffix, staffRole, 1);
    requester = login(ownerName, password);
    host = login(hostName, password);
    desk = login(deskName, password);
    stranger = login("stranger-" + suffix, password);
    badge =
        ok(
                desk,
                "POST",
                "/badges",
                Map.of(
                    "code",
                    "B-" + suffix,
                    "name",
                    "验收测试访客牌",
                    "departmentId",
                    dept,
                    "enabled",
                    true))
            .path("id")
            .asLong();
    v = ok(requester, "POST", "/visits", draft(start, end));
    id = v.path("id").asLong();
  }

  long user(String name, long role, long department) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                name,
                "displayName",
                "验收测试 " + name,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                department,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> draft(Instant from, Instant to) {
    return Map.of(
        "visitorName",
        "验收测试来访人",
        "organization",
        "验收测试单位",
        "purpose",
        "接待验收",
        "hostId",
        hostId,
        "category",
        "BUSINESS",
        "startsAt",
        from,
        "endsAt",
        to,
        "requestKey",
        UUID.randomUUID().toString());
  }

  JsonNode current() throws Exception {
    return ok(requester, "GET", "/visits/" + id, null).path("visit");
  }

  Map<String, Object> command(String note) throws Exception {
    return Map.of(
        "version",
        current().path("version").asLong(),
        "requestKey",
        UUID.randomUUID().toString(),
        "note",
        note,
        "badgeId",
        badge,
        "identityConfirmed",
        true,
        "badgeReturned",
        true);
  }

  JsonNode act(MockHttpSession session, String action) throws Exception {
    return ok(session, "POST", "/visits/" + id + "/commands/" + action, command("验收测试处理"));
  }

  void confirmed() throws Exception {
    act(requester, "submit");
    act(host, "approve");
  }

  MockHttpSession login(String name, String pw) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", name, "password", pw))))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession();
  }

  MvcResult call(MockHttpSession who, String method, String path, Object body) throws Exception {
    var req =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    req.session(who).with(csrf());
    if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(req).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var res = call(who, method, path, body);
    assertEquals(200, res.getResponse().getStatus(), res.getResponse().getContentAsString());
    return json.readTree(res.getResponse().getContentAsString());
  }

  void expect(MockHttpSession who, String method, String path, Object body, int status)
      throws Exception {
    var res = call(who, method, path, body);
    assertEquals(status, res.getResponse().getStatus(), res.getResponse().getContentAsString());
  }

  @Test
  void fullReception() throws Exception {
    confirmed();
    act(desk, "check-in");
    assertEquals("IN_SITE", current().path("status").asString());
    act(desk, "check-out");
    assertEquals("LEFT", current().path("status").asString());
    assertEquals(5, ok(requester, "GET", "/visits/" + id, null).path("events").size());
  }

  @Test
  void rejectionAndResubmit() throws Exception {
    act(requester, "submit");
    act(host, "reject");
    assertEquals("REJECTED", current().path("status").asString());
    var d = new HashMap<>(draft(start, end));
    d.put("version", current().path("version").asLong());
    ok(requester, "PUT", "/visits/" + id, d);
    act(requester, "submit");
    act(host, "approve");
  }

  @Test
  void requesterCannotConfirm() throws Exception {
    act(requester, "submit");
    expect(requester, "POST", "/visits/" + id + "/commands/approve", command(""), 403);
  }

  @Test
  void strangerCannotRead() throws Exception {
    expect(stranger, "GET", "/visits/" + id, null, 403);
  }

  @Test
  void strangerCannotExport() throws Exception {
    expect(stranger, "GET", "/visits/" + id + "/report.json", null, 403);
  }

  @Test
  void employeeCannotReceive() throws Exception {
    confirmed();
    expect(requester, "POST", "/visits/" + id + "/commands/check-in", command(""), 403);
  }

  @Test
  void pendingCannotEnter() throws Exception {
    act(requester, "submit");
    expect(desk, "POST", "/visits/" + id + "/commands/check-in", command(""), 409);
  }

  @Test
  void badgeRequired() throws Exception {
    confirmed();
    var c = new HashMap<>(command(""));
    c.remove("badgeId");
    expect(desk, "POST", "/visits/" + id + "/commands/check-in", c, 400);
  }

  @Test
  void manualVerificationRequired() throws Exception {
    confirmed();
    var c = new HashMap<>(command(""));
    c.put("identityConfirmed", false);
    expect(desk, "POST", "/visits/" + id + "/commands/check-in", c, 400);
  }

  @Test
  void returnConfirmationRequired() throws Exception {
    confirmed();
    act(desk, "check-in");
    var c = new HashMap<>(command(""));
    c.put("badgeReturned", false);
    expect(desk, "POST", "/visits/" + id + "/commands/check-out", c, 400);
    assertEquals("IN_SITE", current().path("status").asString());
  }

  @Test
  void badgeCannotIssueTwice() throws Exception {
    confirmed();
    act(desk, "check-in");
    long first = id;
    v = ok(requester, "POST", "/visits", draft(start, end));
    id = v.path("id").asLong();
    confirmed();
    expect(desk, "POST", "/visits/" + id + "/commands/check-in", command(""), 409);
    id = first;
    act(desk, "check-out");
  }

  @Test
  void badgeReleasedAfterExit() throws Exception {
    confirmed();
    act(desk, "check-in");
    act(desk, "check-out");
    v = ok(requester, "POST", "/visits", draft(start, end));
    id = v.path("id").asLong();
    confirmed();
    act(desk, "check-in");
  }

  @Test
  void occupiedBadgeCannotDisable() throws Exception {
    confirmed();
    act(desk, "check-in");
    var b = ok(desk, "GET", "/badges", null).iterator();
    JsonNode found = null;
    while (b.hasNext()) {
      var x = b.next().path("badge");
      if (x.path("id").asLong() == badge) found = x;
    }
    expect(
        desk,
        "PUT",
        "/badges/" + badge,
        Map.of(
            "version",
            found.path("version").asLong(),
            "code",
            found.path("code").asString(),
            "name",
            "测试",
            "departmentId",
            dept,
            "enabled",
            false),
        409);
  }

  @Test
  void tooEarlyArrivalBlocked() throws Exception {
    confirmed();
    clock.value = start.minusSeconds(1801);
    expect(desk, "POST", "/visits/" + id + "/commands/check-in", command(""), 409);
  }

  @Test
  void expiredCannotEnterEvenBeforeSweep() throws Exception {
    confirmed();
    clock.value = end;
    expect(desk, "POST", "/visits/" + id + "/commands/check-in", command(""), 409);
  }

  @Test
  void pendingExpiresAtEnd() throws Exception {
    act(requester, "submit");
    clock.value = end;
    service.expire(id);
    assertEquals("EXPIRED", current().path("status").asString());
  }

  @Test
  void onsiteNeverAutomaticallyLeaves() throws Exception {
    confirmed();
    act(desk, "check-in");
    clock.value = end.plusSeconds(60);
    service.expire(id);
    assertEquals("IN_SITE", current().path("status").asString());
    assertTrue(ok(desk, "GET", "/dashboard", null).path("overdue").asLong() >= 1);
    act(desk, "check-out");
  }

  @Test
  void hostRevokesConfirmation() throws Exception {
    confirmed();
    act(host, "revoke");
    assertEquals("CANCELLED", current().path("status").asString());
  }

  @Test
  void deskDeniesEntry() throws Exception {
    confirmed();
    act(desk, "deny");
    assertEquals("DENIED", current().path("status").asString());
  }

  @Test
  void onsiteCannotCancel() throws Exception {
    confirmed();
    act(desk, "check-in");
    expect(requester, "POST", "/visits/" + id + "/commands/cancel", command("原因"), 409);
  }

  @Test
  void submittedHistoryCannotDelete() throws Exception {
    act(requester, "submit");
    expect(
        requester,
        "DELETE",
        "/visits/" + id + "?version=" + current().path("version").asLong(),
        null,
        409);
  }

  @Test
  void draftCanDelete() throws Exception {
    expect(
        requester,
        "DELETE",
        "/visits/" + id + "?version=" + current().path("version").asLong(),
        null,
        200);
    expect(requester, "GET", "/visits/" + id, null, 404);
  }

  @Test
  void staleSaveRejected() throws Exception {
    var d = new HashMap<>(draft(start, end));
    d.put("version", -1);
    expect(requester, "PUT", "/visits/" + id, d, 409);
  }

  @Test
  void sameCommandReplaysOnlyOnce() throws Exception {
    var c = command("同键重试");
    var first = ok(requester, "POST", "/visits/" + id + "/commands/submit", c);
    var again = ok(requester, "POST", "/visits/" + id + "/commands/submit", c);
    assertEquals(first.path("version"), again.path("version"));
    assertEquals(2, ok(requester, "GET", "/visits/" + id, null).path("events").size());
  }

  @Test
  void changedRetryRejected() throws Exception {
    var c = new HashMap<>(command("原请求"));
    ok(requester, "POST", "/visits/" + id + "/commands/submit", c);
    c.put("note", "另一请求");
    expect(requester, "POST", "/visits/" + id + "/commands/submit", c, 409);
  }

  @Test
  void createReplaysOnlyOnce() throws Exception {
    var d = draft(start, end);
    assertEquals(
        ok(requester, "POST", "/visits", d).path("id"),
        ok(requester, "POST", "/visits", d).path("id"));
  }

  @Test
  void independentHostRequired() throws Exception {
    var d = new HashMap<>(draft(start, end));
    d.put("hostId", ownerId);
    expect(requester, "POST", "/visits", d, 400);
  }

  @Test
  void crossDepartmentReceptionRejected() throws Exception {
    confirmed();
    expect(stranger, "GET", "/visits?onsite=true", null, 403);
    expect(stranger, "GET", "/badges", null, 403);
  }

  @Test
  void visitSearchAndPagingScoped() throws Exception {
    var a = ok(requester, "GET", "/visits?search=验收&size=1", null);
    assertEquals(1, a.path("items").size());
    var b = ok(stranger, "GET", "/visits", null);
    assertTrue(b.path("items").findValues("id").stream().noneMatch(n -> n.asLong() == id));
  }

  @Test
  void invalidSortAndPageRejected() throws Exception {
    expect(requester, "GET", "/visits?sort=arbitrary", null, 400);
    expect(requester, "GET", "/visits?size=1000", null, 400);
  }

  @Test
  void optionDirectoryContainsNoCredential() throws Exception {
    var text = ok(requester, "GET", "/options", null).toString();
    assertFalse(text.contains("passwordHash"));
    assertFalse(text.contains("username"));
    assertFalse(text.contains("visitorName"));
  }

  @Test
  void adminEndpointsProtected() throws Exception {
    expect(requester, "GET", "/admin/users", null, 403);
    expect(desk, "GET", "/admin/roles", null, 403);
  }

  @Test
  void lastAdministratorProtected() throws Exception {
    var a = ok(admin, "GET", "/auth/me", null);
    expect(
        admin,
        "PUT",
        "/admin/users/" + a.path("id").asLong(),
        Map.of(
            "username",
            "admin",
            "displayName",
            "管理员",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            false),
        409);
  }

  @Test
  void missingCsrfRejected() throws Exception {
    mvc.perform(
            post("/api/visits")
                .session(requester)
                .contentType("application/json")
                .content(json.writeValueAsString(draft(start, end))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
  }

  @Test
  void concurrentBadgeHasSingleWinner() throws Exception {
    confirmed();
    long first = id;
    var firstCmd = command("");
    v = ok(requester, "POST", "/visits", draft(start, end));
    id = v.path("id").asLong();
    confirmed();
    long second = id;
    var secondCmd = command("");
    long role = 0;
    for (var x : ok(admin, "GET", "/admin/roles", null))
      if (x.path("name").asString().equals("前台")) role = x.path("id").asLong();
    user("desk2-" + suffix, role, dept);
    var desk2 = login("desk2-" + suffix, password);
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return call(desk, "POST", "/visits/" + first + "/commands/check-in", firstCmd)
                    .getResponse()
                    .getStatus();
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return call(desk2, "POST", "/visits/" + second + "/commands/check-in", secondCmd)
                    .getResponse()
                    .getStatus();
              });
      gate.countDown();
      var results = new ArrayList<>(List.of(a.get(), b.get()));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void receptionWithoutBadgeEditingStillReceives() throws Exception {
    long role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "接待限定-" + suffix,
                    "scope",
                    "DEPARTMENT",
                    "permissions",
                    Set.of("visit.read", "reception")))
            .path("id")
            .asLong();
    user("limited-" + suffix, role, dept);
    var limited = login("limited-" + suffix, password);
    ok(limited, "GET", "/badges", null);
    expect(
        limited,
        "POST",
        "/badges",
        Map.of("code", "X", "name", "测试", "departmentId", dept, "enabled", true),
        403);
    confirmed();
    act(limited, "check-in");
  }

  @Test
  void disabledAccountSessionImmediatelyFails() throws Exception {
    ok(
        admin,
        "PUT",
        "/admin/users/" + ownerId,
        Map.of(
            "username",
            ownerName,
            "displayName",
            "停用验收",
            "roleId",
            staffRole,
            "departmentId",
            dept,
            "enabled",
            false));
    expect(requester, "GET", "/auth/me", null, 401);
    expect(requester, "GET", "/visits", null, 401);
  }

  @Test
  void weakPasswordCannotBeStored() throws Exception {
    expect(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            "weak-" + suffix,
            "displayName",
            "弱密码验收",
            "roleId",
            staffRole,
            "departmentId",
            dept,
            "enabled",
            true,
            "password",
            "short"),
        400);
  }

  @Test
  void submittedArrivalRuleIsSnapshot() throws Exception {
    confirmed();
    JsonNode setting = null;
    for (var x : ok(admin, "GET", "/admin/settings", null))
      if (x.path("code").asString().equals("earlyArrivalMinutes")) setting = x;
    long settingId = setting.path("id").asLong();
    try {
      ok(admin, "PUT", "/admin/settings/" + settingId, Map.of("value", "0"));
      act(desk, "check-in");
      assertEquals("IN_SITE", current().path("status").asString());
    } finally {
      ok(admin, "PUT", "/admin/settings/" + settingId, Map.of("value", "30"));
    }
  }
}
