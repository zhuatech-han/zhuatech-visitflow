// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 单实例定时标记未到访的过期记录；实际在场者继续保留。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class VisitScheduler {
  final VisitService service;

  public VisitScheduler(VisitService service) {
    this.service = service;
  }

  /** 每个来访独立事务更新，失败不自动假定离场。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(
      fixedDelayString = "${visitflow.sweep-millis:60000}",
      initialDelayString = "${visitflow.sweep-millis:60000}")
  public void sweep() {
    for (var id : service.sweepIds()) service.expire(id);
  }
}
