// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import jakarta.persistence.*;
import java.time.Instant;

/** 来访登记、被访人及接待事实；在场记录不会自动离场。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "visitor_visit")
public class Visit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "host_id", nullable = false)
  public Long hostId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "visitor_name", nullable = false, length = 120)
  public String visitorName;

  @Column(name = "organization", nullable = false, length = 200)
  public String organization;

  @Column(name = "purpose", nullable = false, length = 2000)
  public String purpose;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "checked_in_at", nullable = true)
  public Instant checkedInAt;

  @Column(name = "checked_out_at", nullable = true)
  public Instant checkedOutAt;

  @Column(name = "badge_id", nullable = true)
  public Long badgeId;

  @Column(name = "submitted", nullable = false)
  public boolean submitted;

  @Column(name = "early_minutes", nullable = false)
  public int earlyMinutes;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
