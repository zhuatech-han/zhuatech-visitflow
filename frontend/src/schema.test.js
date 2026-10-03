// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { localInput, instant, editable, actions } from "./schema.js";
const staff = {
  id: 1,
  departmentId: 10,
  scope: "ASSIGNED",
  permissions: ["visit.read", "visit.write", "visit.approve"],
};
const desk = {
  id: 3,
  departmentId: 10,
  scope: "DEPARTMENT",
  permissions: ["visit.read", "reception"],
};
const visit = { ownerId: 1, hostId: 2, departmentId: 10, status: "PENDING" };
test("Shanghai time round trips", () =>
  assert.equal(
    instant(localInput("2026-10-05T02:00:00Z")),
    "2026-10-05T02:00:00.000Z",
  ));
test("impossible input rejected", () =>
  assert.throws(() => instant("2026-02-30T10:00")));
test("own draft editable", () =>
  assert.equal(editable({ ...visit, status: "DRAFT" }, staff), true));
test("other draft not editable", () =>
  assert.equal(
    editable({ ...visit, status: "DRAFT", ownerId: 9 }, staff),
    false,
  ));
test("requester cannot approve own visit", () =>
  assert.deepEqual(actions(visit, staff), ["cancel"]));
test("designated host can confirm", () =>
  assert.deepEqual(actions(visit, { ...staff, id: 2 }), ["approve", "reject"]));
test("desk can admit confirmed visit", () =>
  assert.deepEqual(actions({ ...visit, status: "CONFIRMED" }, desk), [
    "check-in",
    "deny",
  ]));
test("other department desk cannot admit", () =>
  assert.deepEqual(
    actions({ ...visit, status: "CONFIRMED" }, { ...desk, departmentId: 20 }),
    [],
  ));
test("onsite has checkout and no cancellation", () =>
  assert.deepEqual(actions({ ...visit, status: "IN_SITE" }, desk), [
    "check-out",
  ]));
test("expired has no admission action", () =>
  assert.deepEqual(actions({ ...visit, status: "EXPIRED" }, desk), []));
