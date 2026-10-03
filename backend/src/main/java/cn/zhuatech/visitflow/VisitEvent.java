// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import jakarta.persistence.*;
import java.time.Instant;

/** 不可修改的状态事件与当时来访快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "visit_event")
public class VisitEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "visit_id", nullable = false)
  public Long visitId;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "action", nullable = false, length = 40)
  public String action;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "snapshot", nullable = false, columnDefinition = "text")
  public String snapshot;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
