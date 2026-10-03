// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.visitflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 时刻边界和真实在场规则单元测试。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class VisitPolicyTest {
  final Instant start = Instant.parse("2026-10-05T02:00:00Z"), end = start.plusSeconds(3600);

  @Test
  void validPlan() {
    assertDoesNotThrow(() -> VisitPolicy.times(start, end, start.minusSeconds(900), 90));
  }

  @Test
  void twelveHourPlanAllowed() {
    assertDoesNotThrow(() -> VisitPolicy.times(start, start.plusSeconds(43200), start, 90));
  }

  @Test
  void overTwelveHoursRejected() {
    assertThrows(
        Problem.class, () -> VisitPolicy.times(start, start.plusSeconds(43260), start, 90));
  }

  @Test
  void secondsBeyondLimitRejected() {
    assertThrows(
        Problem.class, () -> VisitPolicy.times(start, start.plusSeconds(43201), start, 90));
  }

  @Test
  void equalTimesRejected() {
    assertThrows(Problem.class, () -> VisitPolicy.times(start, start, start, 90));
  }

  @Test
  void missingStartRejected() {
    assertThrows(Problem.class, () -> VisitPolicy.times(null, end, start, 90));
  }

  @Test
  void pastEndRejected() {
    assertThrows(Problem.class, () -> VisitPolicy.times(start, end, end, 90));
  }

  @Test
  void tooOldWalkinRejected() {
    assertThrows(Problem.class, () -> VisitPolicy.times(start, end, start.plusSeconds(1801), 90));
  }

  @Test
  void exactWalkinBoundaryAccepted() {
    assertDoesNotThrow(() -> VisitPolicy.times(start, end, start.plusSeconds(1800), 90));
  }

  @Test
  void horizonBoundaryAccepted() {
    assertDoesNotThrow(
        () -> VisitPolicy.times(start.plusSeconds(86400), end.plusSeconds(86400), start, 1));
  }

  @Test
  void beyondHorizonRejected() {
    assertThrows(
        Problem.class,
        () -> VisitPolicy.times(start.plusSeconds(86401), end.plusSeconds(86401), start, 1));
  }

  @Test
  void earlyBoundaryAccepted() {
    assertDoesNotThrow(() -> VisitPolicy.arrival(start, end, start.minusSeconds(1800), 30));
  }

  @Test
  void tooEarlyRejected() {
    assertThrows(
        Problem.class, () -> VisitPolicy.arrival(start, end, start.minusSeconds(1801), 30));
  }

  @Test
  void endBoundaryRejected() {
    assertThrows(Problem.class, () -> VisitPolicy.arrival(start, end, end, 30));
  }

  @Test
  void expiresAtEnd() {
    assertTrue(VisitPolicy.expires("CONFIRMED", end, end));
  }

  @Test
  void onsiteNeverExpires() {
    assertFalse(VisitPolicy.expires("IN_SITE", end, end.plusSeconds(600)));
  }
}
