// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 同源来访、接待、访客牌和管理接口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final VisitService service;
  final AdminService admin;

  public ApiController(VisitService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 读取授权options。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 读取授权workbench。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 读取授权dashboard。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 读取授权audit。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 读取授权badges。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/badges")
  public Object badges() {
    return service.badges();
  }

  /** 分页检索授权来访。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/visits")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "time") String sort,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "false") boolean onsite) {
    return service.list(search, status, page, size, sort, mine, onsite);
  }

  /** 创建本人草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/visits")
  public Object create(@RequestBody VisitService.Draft v) {
    return service.create(v);
  }

  /** 保存本人草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/visits/{id}")
  public Object save(@PathVariable Long id, @RequestBody VisitService.Draft v) {
    return service.save(id, v);
  }

  /** 查看来访与历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/visits/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 仅删除未送审草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/visits/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 验证权限、版本及状态后接待流转。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/visits/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody VisitService.Command v) {
    return service.act(id, action, v);
  }

  /** 建立访客牌。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/badges")
  public Object createBadge(@RequestBody VisitService.BadgeInput v) {
    return service.saveBadge(null, v);
  }

  /** 修改未占用访客牌。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/badges/{id}")
  public Object saveBadge(@PathVariable Long id, @RequestBody VisitService.BadgeInput v) {
    return service.saveBadge(id, v);
  }

  /** 删除未引用访客牌。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/badges/{id}")
  public Object deleteBadge(@PathVariable Long id, @RequestParam Long version) {
    service.deleteBadge(id, version);
    return Map.of("ok", true);
  }

  /** 授权快照下载，不返回凭证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/visits/{id}/report.json")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=visit-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.export(id));
  }

  /** 查询系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object listAdmin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除无引用的系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
