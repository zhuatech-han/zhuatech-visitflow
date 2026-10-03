// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import java.time.*;

/** 来访期限与入场窗口的纯规则，不把计划结束当成实际离场。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class VisitPolicy {
  private VisitPolicy() {}

  /** 校验单次不超过十二小时的未来来访，可登记开始前后30分钟的临时来访。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void times(Instant start, Instant end, Instant now, int horizon) {
    if (start == null
        || end == null
        || !end.isAfter(start)
        || Duration.between(start, end).compareTo(Duration.ofHours(12)) > 0
        || !end.isAfter(now)) throw new Problem(400, "INVALID_TIME");
    if (start.isBefore(now.minusSeconds(1800)) || start.isAfter(now.plusSeconds(horizon * 86400L)))
      throw new Problem(400, "VISIT_WINDOW");
  }

  /** 入场从配置的提前分钟开始，计划结束时刻不再接受入场。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void arrival(Instant start, Instant end, Instant now, int early) {
    if (now.isBefore(start.minusSeconds(early * 60L)) || !now.isBefore(end))
      throw new Problem(409, "ARRIVAL_WINDOW");
  }

  /** 判断未入场记录是否过期，实际在场记录永远不自动签离。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean expires(String state, Instant end, Instant now) {
    return java.util.Set.of("PENDING", "CONFIRMED").contains(state) && !now.isBefore(end);
  }
}
