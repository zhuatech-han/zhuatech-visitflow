// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** 来访登记、被访确认、前台进出和访客牌互斥；保护真实在场事实。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class VisitService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public VisitService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 登记输入，服务端决定申请人、部门和状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long version,
      Long hostId,
      String visitorName,
      String organization,
      String purpose,
      String category,
      Instant startsAt,
      Instant endsAt,
      String requestKey) {}

  /** 状态命令包含版本、重试键和现场人工确认；不接收可伪造的实际时间。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      Long version,
      String requestKey,
      String note,
      Long badgeId,
      Boolean identityConfirmed,
      Boolean badgeReturned) {}

  /** 访客牌主数据输入；历史关联牌不能改部门或代码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record BadgeInput(
      Long version, String code, String name, Long departmentId, Boolean enabled) {}

  /** 登录账号可读非敏感岗位目录，不返回用户名、访客或密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    var me = access.current();
    var hosts =
        db.all(Account.class).stream()
            .filter(
                a ->
                    a.enabled
                        && !a.id.equals(me.id)
                        && db.get(AccessRole.class, a.roleId)
                            .permissions
                            .containsAll(Set.of("visit.read", "visit.approve")))
            .map(a -> Map.of("id", a.id, "name", a.displayName, "departmentId", a.departmentId))
            .toList();
    return Map.of(
        "hosts",
        hosts,
        "departments",
        db.all(Department.class),
        "dictionaries",
        db.all(DictionaryEntry.class).stream().filter(d -> d.type.equals("visit")).toList(),
        "settings",
        db.all(SystemSetting.class),
        "serverNow",
        clock.instant());
  }

  /** 分页检索授权来访；在场接口还要求接待权限，不能泄露其他部门名单。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String search, String status, int page, int size, String sort, boolean mine, boolean onsite) {
    access.require("visit.read");
    if (onsite) access.require("reception");
    if (search == null
        || search.length() > 120
        || page < 0
        || page > 100000
        || size < 1
        || size > 100) throw new Problem(400, "INVALID_INPUT");
    if (!status.isBlank() && !states().contains(status)) throw new Problem(400, "INVALID_STATE");
    String where =
        scope()
            + " and (lower(v.visitorName) like :q escape '!' or lower(v.organization) like :q escape '!')";
    if (mine) where += " and v.ownerId=" + access.current().id;
    if (onsite) where += " and v.status='IN_SITE'";
    if (!status.isBlank()) where += " and v.status=:s";
    String order =
        switch (sort) {
          case "time" -> "v.startsAt asc,v.id asc";
          case "newest" -> "v.createdAt desc,v.id desc";
          case "name" -> "v.visitorName asc,v.id asc";
          default -> throw new Problem(400, "INVALID_INPUT");
        };
    String q =
        "%"
            + search
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    var count =
        db.jpql(Long.class, "select count(v) from Visit v where " + where).setParameter("q", q);
    var rows =
        db.jpql(Visit.class, "from Visit v where " + where + " order by " + order)
            .setParameter("q", q);
    if (!status.isBlank()) {
      count.setParameter("s", status);
      rows.setParameter("s", status);
    }
    return Map.of(
        "items",
        rows.setFirstResult(page * size).setMaxResults(size).getResultList(),
        "total",
        count.getSingleResult());
  }

  /** 创建本人草稿，按请求键防止重复登记。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Visit create(Draft v) {
    access.require("visit.read");
    access.require("visit.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    actorLock();
    String fp = fingerprint("create", v);
    var prev = previous(v.requestKey, fp);
    if (prev != null) {
      read(prev);
      return prev;
    }
    var visit = new Visit();
    visit.ownerId = access.current().id;
    fill(visit, v);
    visit.status = "DRAFT";
    visit.createdAt = clock.instant();
    visit.updatedAt = clock.instant();
    db.save(visit);
    db.flush();
    stamp(visit, v.requestKey, fp);
    event(visit, "CREATE", "");
    return visit;
  }

  /** 仅本人未送审或退回草稿可编辑；已审批信息冻结。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Visit save(Long id, Draft v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    access.require("visit.read");
    access.require("visit.write");
    actorLock();
    var a = db.lock(Visit.class, id);
    read(a);
    owner(a);
    String fp = fingerprint("save:" + id, v);
    var prev = previous(v.requestKey, fp);
    if (prev != null) {
      if (!prev.id.equals(id)) throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return a;
    }
    version(a.version, v.version);
    state(a, "DRAFT", "REJECTED");
    fill(a, v);
    a.updatedAt = clock.instant();
    db.flush();
    stamp(a, v.requestKey, fp);
    event(a, "SAVE", "");
    return a;
  }

  /** 保护提交历史，仅从未提交的本人草稿可物理删除。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long version) {
    access.require("visit.read");
    access.require("visit.write");
    actorLock();
    var v = db.lock(Visit.class, id);
    read(v);
    owner(v);
    version(v.version, version);
    if (!v.status.equals("DRAFT") || v.submitted) throw new Problem(409, "HISTORY_PROTECTED");
    for (var c : db.query(VisitCommand.class, "from VisitCommand where visitId=?1", id))
      db.delete(c);
    for (var e : db.query(VisitEvent.class, "from VisitEvent where visitId=?1", id)) db.delete(e);
    access.audit("DRAFT_DELETE", id, v.departmentId);
    db.delete(v);
  }

  /** 返回受权限保护的来访、事件和非敏感被访人名称。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("visit.read");
    var v = db.get(Visit.class, id);
    read(v);
    return Map.of(
        "visit",
        v,
        "hostName",
        db.get(Account.class, v.hostId).displayName,
        "ownerName",
        db.get(Account.class, v.ownerId).displayName,
        "badgeCode",
        v.badgeId == null ? "" : db.get(VisitorBadge.class, v.badgeId).code,
        "events",
        db.query(VisitEvent.class, "from VisitEvent where visitId=?1 order by id", id));
  }

  /** 有限状态流转；先锁操作者、来访，再锁访客牌，同牌只能给一人在场。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Visit act(Long id, String action, Command c) {
    if (c == null) throw new Problem(400, "INVALID_INPUT");
    access.require("visit.read");
    actorLock();
    var v = db.lock(Visit.class, id);
    read(v);
    authorize(v, action);
    String fp = fingerprint(action + ":" + id, c);
    var prev = previous(c.requestKey, fp);
    if (prev != null) {
      if (!prev.id.equals(id)) throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return v;
    }
    version(v.version, c.version);
    String note = c.note == null ? "" : c.note.trim();
    if (note.length() > 1000) throw new Problem(400, "INVALID_INPUT");
    var now = clock.instant();
    switch (action) {
      case "submit" -> {
        state(v, "DRAFT", "REJECTED");
        if (!host(v.hostId).departmentId.equals(v.departmentId))
          throw new Problem(400, "INVALID_HOST");
        VisitPolicy.times(v.startsAt, v.endsAt, now, setting("visitHorizonDays"));
        v.earlyMinutes = setting("earlyArrivalMinutes");
        v.submitted = true;
        v.status = "PENDING";
      }
      case "approve" -> {
        state(v, "PENDING");
        if (!host(v.hostId).departmentId.equals(v.departmentId))
          throw new Problem(400, "INVALID_HOST");
        if (!v.endsAt.isAfter(now)) throw new Problem(409, "VISIT_EXPIRED");
        v.status = "CONFIRMED";
      }
      case "reject" -> {
        state(v, "PENDING");
        note = AdminService.text(note, 1000);
        v.status = "REJECTED";
      }
      case "cancel", "revoke", "deny" -> {
        state(
            v,
            action.equals("cancel")
                ? new String[] {"DRAFT", "REJECTED", "PENDING", "CONFIRMED"}
                : new String[] {"CONFIRMED"});
        note = AdminService.text(note, 1000);
        v.status = action.equals("deny") ? "DENIED" : "CANCELLED";
      }
      case "check-in" -> {
        state(v, "CONFIRMED");
        if (!host(v.hostId).departmentId.equals(v.departmentId))
          throw new Problem(400, "INVALID_HOST");
        VisitPolicy.arrival(v.startsAt, v.endsAt, now, v.earlyMinutes);
        if (!Boolean.TRUE.equals(c.identityConfirmed))
          throw new Problem(400, "IDENTITY_CONFIRMATION_REQUIRED");
        if (c.badgeId == null) throw new Problem(400, "BADGE_REQUIRED");
        var b = db.lock(VisitorBadge.class, c.badgeId);
        access.department(b.departmentId);
        if (!Objects.equals(b.departmentId, v.departmentId) || !b.enabled)
          throw new Problem(409, "BADGE_UNAVAILABLE");
        if (!db.query(Visit.class, "from Visit where badgeId=?1 and status='IN_SITE'", b.id)
            .isEmpty()) throw new Problem(409, "BADGE_IN_USE");
        v.badgeId = b.id;
        v.status = "IN_SITE";
        v.checkedInAt = now;
      }
      case "check-out" -> {
        state(v, "IN_SITE");
        if (!Boolean.TRUE.equals(c.badgeReturned)) throw new Problem(400, "BADGE_RETURN_REQUIRED");
        note = AdminService.text(note, 1000);
        db.lock(VisitorBadge.class, v.badgeId);
        v.checkedOutAt = now;
        v.status = "LEFT";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    v.updatedAt = now;
    db.flush();
    stamp(v, c.requestKey, fp);
    event(v, action.toUpperCase(Locale.ROOT), note);
    return v;
  }

  /** 当前范围访客牌和实际在用关联，不把已逾时来访当作已归还。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> badges() {
    if (!access.role().permissions.contains("reception")) access.require("badge.manage");
    return db.all(VisitorBadge.class).stream()
        .filter(b -> access.visible(b.departmentId))
        .map(
            b -> {
              var active =
                  db.query(Visit.class, "from Visit where badgeId=?1 and status='IN_SITE'", b.id);
              return Map.<String, Object>of("badge", b, "inUse", !active.isEmpty());
            })
        .toList();
  }

  /** 新建或版本化更新访客牌；被历史引用的牌编号和部门冻结，在用牌不可停用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public VisitorBadge saveBadge(Long id, BadgeInput v) {
    if (v == null || v.departmentId == null) throw new Problem(400, "INVALID_INPUT");
    access.require("badge.manage");
    actorLock();
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    var b = id == null ? new VisitorBadge() : db.lock(VisitorBadge.class, id);
    if (id != null) {
      access.department(b.departmentId);
      version(b.version, v.version);
      var refs = db.query(Visit.class, "from Visit where badgeId=?1", id);
      if (!refs.isEmpty()
          && (!Objects.equals(b.code, v.code) || !Objects.equals(b.departmentId, v.departmentId)))
        throw new Problem(409, "HISTORY_PROTECTED");
      if (refs.stream().anyMatch(a -> a.status.equals("IN_SITE"))
          && !Boolean.TRUE.equals(v.enabled)) throw new Problem(409, "BADGE_IN_USE");
    }
    var code = AdminService.text(v.code, 60).toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9_-]{1,60}")) throw new Problem(400, "INVALID_INPUT");
    b.code = code;
    b.name = AdminService.text(v.name, 120);
    b.departmentId = v.departmentId;
    b.enabled = Boolean.TRUE.equals(v.enabled);
    if (id == null) db.save(b);
    db.flush();
    access.audit("BADGE_SAVE", b.id, b.departmentId);
    return b;
  }

  /** 删除未引用访客牌，已发放历史由外键与业务校验保护。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteBadge(Long id, Long version) {
    access.require("badge.manage");
    actorLock();
    var b = db.lock(VisitorBadge.class, id);
    access.department(b.departmentId);
    version(b.version, version);
    if (!db.query(Visit.class, "from Visit where badgeId=?1", id).isEmpty())
      throw new Problem(409, "HISTORY_PROTECTED");
    access.audit("BADGE_DELETE", id, b.departmentId);
    db.delete(b);
  }

  /** 本人来访和指定被访人的待确认，最多一万条时明确拒绝统计。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("visit.read");
    var all = visible();
    var id = access.current().id;
    return Map.of(
        "mine",
        all.stream()
            .filter(
                v ->
                    v.ownerId.equals(id)
                        && Set.of("DRAFT", "REJECTED", "PENDING", "CONFIRMED", "IN_SITE")
                            .contains(v.status))
            .toList(),
        "reviews",
        all.stream().filter(v -> v.hostId.equals(id) && v.status.equals("PENDING")).toList());
  }

  /** 范围内来访数量、在场逾时及已完成接待分钟；不自动减少真实在场人数。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("visit.read");
    access.require("dashboard");
    var all = visible();
    var now = clock.instant();
    var counts = new TreeMap<String, Long>();
    for (var s : states()) counts.put(s, all.stream().filter(v -> v.status.equals(s)).count());
    long minutes =
        all.stream()
            .filter(v -> v.checkedOutAt != null)
            .mapToLong(v -> Duration.between(v.checkedInAt, v.checkedOutAt).toMinutes())
            .sum();
    return Map.of(
        "total",
        all.size(),
        "pending",
        counts.get("PENDING"),
        "onsite",
        counts.get("IN_SITE"),
        "overdue",
        all.stream().filter(v -> v.status.equals("IN_SITE") && !v.endsAt.isAfter(now)).count(),
        "minutes",
        minutes,
        "states",
        counts);
  }

  /** 授权部门的操作审计，不包含姓名或来访载荷。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            a ->
                access.role().scope.equals("ALL")
                    || access.role().scope.equals("DEPARTMENT") && access.visible(a.departmentId)
                    || a.actor.equals(access.current().username))
        .toList();
  }

  /** 导出已授权单据与历史，响应不夹带品牌广告、会话或账号凭证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    access.require("export");
    return json.writeValueAsString(detail(id));
  }

  /** 系统扫描未入场且到期的单据ID，后续逐单独立事务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Long> sweepIds() {
    return db.jpql(
            Long.class,
            "select id from Visit where status in ('PENDING','CONFIRMED') and endsAt<=:now")
        .setParameter("now", clock.instant())
        .setMaxResults(10000)
        .getResultList();
  }

  /** 单据行锁下再次检查到期；在场单据不会自动离场。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void expire(Long id) {
    var v = db.lock(Visit.class, id);
    if (VisitPolicy.expires(v.status, v.endsAt, clock.instant())) {
      v.status = "EXPIRED";
      v.updatedAt = clock.instant();
      db.flush();
      var e = new VisitEvent();
      e.visitId = id;
      e.actor = "SYSTEM";
      e.action = "EXPIRED";
      e.note = "";
      e.snapshot = json.writeValueAsString(v);
      e.createdAt = clock.instant();
      db.save(e);
    }
  }

  private void fill(Visit a, Draft v) {
    var h = host(v.hostId);
    if (h.id.equals(a.ownerId)) throw new Problem(400, "INDEPENDENT_HOST");
    VisitPolicy.times(v.startsAt, v.endsAt, clock.instant(), setting("visitHorizonDays"));
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='visit' and code=?1",
            v.category)
        .isEmpty()) throw new Problem(400, "INVALID_DICTIONARY");
    a.hostId = h.id;
    a.departmentId = h.departmentId;
    a.visitorName = AdminService.text(v.visitorName, 120);
    a.organization = AdminService.text(v.organization, 200);
    a.purpose = AdminService.text(v.purpose, 2000);
    a.category = v.category;
    a.startsAt = v.startsAt;
    a.endsAt = v.endsAt;
  }

  private Account host(Long id) {
    if (id == null) throw new Problem(400, "INVALID_HOST");
    var a = db.get(Account.class, id);
    if (!a.enabled
        || !db.get(AccessRole.class, a.roleId)
            .permissions
            .containsAll(Set.of("visit.approve", "visit.read")))
      throw new Problem(400, "INVALID_HOST");
    return a;
  }

  private void read(Visit v) {
    if (!db.jpql(Long.class, "select count(v) from Visit v where v.id=:id and " + scope())
        .setParameter("id", v.id)
        .getSingleResult()
        .equals(1L)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private String scope() {
    var a = access.current();
    var r = access.role();
    if (r.scope.equals("ALL")) return "1=1";
    String own = "(v.ownerId=" + a.id + " or v.hostId=" + a.id + ")";
    return r.scope.equals("DEPARTMENT")
        ? "(" + own + " or v.departmentId=" + a.departmentId + ")"
        : own;
  }

  private List<Visit> visible() {
    var list =
        db.jpql(Visit.class, "from Visit v where " + scope()).setMaxResults(10001).getResultList();
    if (list.size() > 10000) throw new Problem(400, "REPORT_LIMIT");
    return list;
  }

  private void owner(Visit v) {
    if (!v.ownerId.equals(access.current().id)) throw new Problem(403, "NOT_OWNER");
  }

  private void authorize(Visit v, String a) {
    switch (a) {
      case "submit", "cancel" -> {
        access.require("visit.write");
        owner(v);
      }
      case "approve", "reject", "revoke" -> {
        access.require("visit.approve");
        if (!v.hostId.equals(access.current().id) || v.ownerId.equals(access.current().id))
          throw new Problem(403, "NOT_HOST");
      }
      case "check-in", "check-out", "deny" -> {
        access.require("reception");
        access.department(v.departmentId);
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
  }

  private void actorLock() {
    var a = db.lock(Account.class, access.current().id);
    db.refresh(a);
    access.current();
  }

  private void version(Long actual, Long provided) {
    if (provided == null || !provided.equals(actual)) throw new Problem(409, "STALE_VERSION");
  }

  private void state(Visit v, String... allowed) {
    if (!Arrays.asList(allowed).contains(v.status)) throw new Problem(409, "INVALID_STATE");
  }

  private int setting(String code) {
    return Integer.parseInt(
        db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value);
  }

  private Set<String> states() {
    return Set.of(
        "DRAFT",
        "PENDING",
        "CONFIRMED",
        "REJECTED",
        "CANCELLED",
        "DENIED",
        "IN_SITE",
        "LEFT",
        "EXPIRED");
  }

  private String fingerprint(String action, Object body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (action + "|" + json.writeValueAsString(body))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Visit previous(String key, String fp) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,80}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows =
        db.query(
            VisitCommand.class,
            "from VisitCommand where actor=?1 and requestKey=?2",
            access.current().username,
            key);
    if (rows.isEmpty()) return null;
    var c = rows.getFirst();
    if (!c.fingerprint.equals(fp)) throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return db.get(Visit.class, c.visitId);
  }

  private void stamp(Visit v, String key, String fp) {
    var c = new VisitCommand();
    c.visitId = v.id;
    c.actor = access.current().username;
    c.requestKey = key;
    c.fingerprint = fp;
    db.save(c);
  }

  private void event(Visit v, String action, String note) {
    var e = new VisitEvent();
    e.visitId = v.id;
    e.actor = access.current().username;
    e.action = action;
    e.note = note;
    e.snapshot = json.writeValueAsString(v);
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, v.id, v.departmentId);
  }
}
