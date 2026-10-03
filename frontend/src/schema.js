// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: ["草稿", "Draft"],
  PENDING: ["待确认", "Pending"],
  CONFIRMED: ["已确认", "Confirmed"],
  IN_SITE: ["在场", "Onsite"],
  LEFT: ["已离场", "Left"],
  REJECTED: ["已退回", "Returned"],
  DENIED: ["已拒绝入场", "Entry denied"],
  CANCELLED: ["已取消", "Cancelled"],
  EXPIRED: ["未到访过期", "Expired"],
};
export const commands = {
  submit: ["提交来访", "Submit"],
  approve: ["确认接待", "Confirm"],
  reject: ["退回修改", "Return"],
  cancel: ["取消来访", "Cancel visit"],
  revoke: ["撤销接待确认", "Revoke"],
  deny: ["拒绝入场", "Deny entry"],
  "check-in": ["登记入场", "Check in"],
  "check-out": ["登记离场", "Check out"],
};
export const labels = {
  visitorName: ["来访人姓名", "Visitor name"],
  organization: ["来访单位", "Organization"],
  purpose: ["来访事由", "Purpose"],
  hostId: ["被访人", "Host"],
  category: ["来访类型", "Visit type"],
  startsAt: ["计划开始（上海时区）", "Start (Shanghai time)"],
  endsAt: ["计划结束（上海时区）", "End (Shanghai time)"],
  badgeId: ["访客牌", "Visitor badge"],
  identityConfirmed: [
    "已现场核对来访人及接待确认",
    "Visitor and approval checked in person",
  ],
  badgeReturned: ["已收回访客牌", "Badge physically returned"],
  note: ["处理意见", "Note"],
  code: ["编号", "Code"],
  name: ["名称", "Name"],
  nameEn: ["英文名称", "English name"],
  departmentId: ["所属部门", "Department"],
  enabled: ["启用", "Enabled"],
  username: ["登录账号", "Username"],
  displayName: ["显示名称", "Display name"],
  password: ["初始或重置密码", "Initial / reset password"],
  roleId: ["角色", "Role"],
  scope: ["数据范围", "Scope"],
  permissions: ["权限", "Permissions"],
  permissionCode: ["菜单所需权限", "Required permission"],
  position: ["顺序", "Order"],
  type: ["字典类型", "Dictionary type"],
  value: ["参数值", "Value"],
  oldPassword: ["当前密码", "Current password"],
  newPassword: ["新密码", "New password"],
};
export const errors = {
  UNAUTHENTICATED: ["会话失效，请重新登录", "Session expired. Sign in."],
  LOGIN_FAILED: [
    "账号、密码错误或账号停用",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: ["请五分钟后重试", "Retry in five minutes."],
  FORBIDDEN: ["没有操作权限", "Permission denied."],
  OUT_OF_SCOPE: ["没有此记录的数据权限", "Outside your data scope."],
  NOT_OWNER: ["仅登记人可操作", "Requester only."],
  NOT_HOST: ["仅指定被访人可确认", "Designated host only."],
  INVALID_HOST: [
    "被访人须启用且具备接待确认权限，变更部门后请重新登记",
    "Host must be active and authorized; recreate after department change.",
  ],
  INDEPENDENT_HOST: [
    "登记人和被访人不能是同一个账号",
    "Requester and host must differ.",
  ],
  STALE_VERSION: ["记录已更新，请刷新后操作", "Record changed. Refresh first."],
  INVALID_STATE: [
    "状态不支持此操作，请刷新",
    "State does not allow this action.",
  ],
  INVALID_ACTION: ["不支持的操作", "Unknown action."],
  INVALID_TIME: [
    "结束须晚于开始，单次最长12小时且尚未结束",
    "End must follow start, last at most 12 hours and remain in the future.",
  ],
  VISIT_WINDOW: [
    "开始时间超出来访窗口，临时来访最多回溯30分钟",
    "Outside horizon; walk-in start may be at most 30 minutes ago.",
  ],
  ARRIVAL_WINDOW: [
    "尚未到可入场时间，或已超过计划结束时间",
    "Outside arrival window.",
  ],
  VISIT_EXPIRED: ["已超过计划结束时间，不能确认接待", "Visit window ended."],
  BADGE_IN_USE: [
    "访客牌仍在使用，须先登记离场并收回",
    "Badge is in use; check out and return it first.",
  ],
  BADGE_UNAVAILABLE: [
    "访客牌停用或不属于来访部门",
    "Badge disabled or outside visit department.",
  ],
  BADGE_REQUIRED: ["请选择访客牌", "Select a badge."],
  IDENTITY_CONFIRMATION_REQUIRED: [
    "须现场核对来访人及确认状态",
    "In-person visitor and approval check required.",
  ],
  BADGE_RETURN_REQUIRED: [
    "须收回访客牌后才能离场登记",
    "Badge return must be confirmed.",
  ],
  HISTORY_PROTECTED: ["已被业务引用，须保留历史", "History is protected."],
  IDEMPOTENCY_CONFLICT: [
    "重试内容不同，请重新打开操作",
    "Retry content changed. Reopen action.",
  ],
  INVALID_REQUEST_KEY: ["请重新打开操作", "Reopen action."],
  INVALID_DICTIONARY: ["来访类型不存在", "Unknown visit type."],
  INVALID_INPUT: [
    "请检查必填项、长度和时间",
    "Check required fields, lengths and times.",
  ],
  CONFLICT: ["编号重复或记录仍被使用", "Duplicate code or referenced record."],
  LAST_ADMIN: [
    "须保留一个启用的全范围管理员",
    "Keep an active all-data administrator.",
  ],
  WEAK_PASSWORD: [
    "密码至少12位，含大写、小写及数字，UTF-8不超过72字节",
    "Use 12+ characters, upper/lower case, digits and at most 72 UTF-8 bytes.",
  ],
  OLD_PASSWORD_INVALID: ["当前密码错误", "Incorrect current password."],
  BUILTIN_RESOURCE: [
    "内建目录不可删除或修改代码",
    "Built-in catalog is protected.",
  ],
  NOT_FOUND: ["记录不存在", "Record not found."],
  REPORT_LIMIT: [
    "超过一万条记录，请缩小范围",
    "More than 10,000 records. Narrow scope.",
  ],
  INVALID_SETTING: [
    "时区固定；窗口1–180天，提前入场0–120分钟",
    "Fixed timezone; horizon 1–180 days, arrival lead 0–120 minutes.",
  ],
};
/** 以固定上海时区显示日期时间，不依赖浏览器所在时区。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(value) {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
}
/** 将服务器 UTC 时间转为上海本地表单字符串。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(value) {
  if (!value) return "";
  return new Date(new Date(value).getTime() + 8 * 3600000)
    .toISOString()
    .slice(0, 16);
}
/** 上海表单时间转为带时区 ISO 时间，拒绝非法或缺失输入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function instant(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))
    throw new Error("INVALID_TIME");
  const d = new Date(value + ":00+08:00");
  if (!Number.isFinite(d.getTime()) || localInput(d.toISOString()) !== value)
    throw new Error("INVALID_TIME");
  return d.toISOString();
}

/** 判断本人草稿编辑入口，服务端仍验权。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function editable(v, me) {
  return (
    !!v &&
    !!me &&
    v.ownerId === me.id &&
    me.permissions.includes("visit.write") &&
    ["DRAFT", "REJECTED"].includes(v.status)
  );
}
/** 状态和岗位决定按钮；接待动作同时验证部门范围。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(v, me) {
  if (!v || !me) return [];
  const result = [];
  if (v.ownerId === me.id && me.permissions.includes("visit.write")) {
    if (editable(v, me)) result.push("submit");
    if (["DRAFT", "REJECTED", "PENDING", "CONFIRMED"].includes(v.status))
      result.push("cancel");
  }
  if (
    v.hostId === me.id &&
    v.ownerId !== me.id &&
    me.permissions.includes("visit.approve")
  ) {
    if (v.status === "PENDING") result.push("approve", "reject");
    if (v.status === "CONFIRMED") result.push("revoke");
  }
  if (
    me.permissions.includes("reception") &&
    (me.scope === "ALL" || me.departmentId === v.departmentId)
  ) {
    if (v.status === "CONFIRMED") result.push("check-in", "deny");
    if (v.status === "IN_SITE") result.push("check-out");
  }
  return result;
}
