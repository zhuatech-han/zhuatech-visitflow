<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  LayoutDashboard,
  Building2,
  ClipboardList,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Plus,
  Search,
  Clock3,
  ArrowUpRight,
  RefreshCw,
  X,
  CheckCircle2,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  commands,
  labels,
  errors,
  date,
  localInput,
  instant,
  editable,
  actions,
} from "./schema.js";
const me = ref(null),
  lang = ref(localStorage.getItem("visitflow-language") || "zh"),
  login = ref({ username: "", password: "" }),
  page = ref("workbench"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  success = ref(""),
  options = ref({ departments: [], hosts: [], dictionaries: [], settings: [] }),
  badges = ref([]),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("time"),
  mine = ref(false),
  detail = ref(null),
  dialog = ref(null),
  stats = ref({}),
  work = ref({ mine: [], reviews: [] });
const tr = (zh, en) => (lang.value === "zh" ? zh : en);
const pair = (v) => v?.[lang.value === "zh" ? 0 : 1] || "";
const can = (p) => me.value?.permissions.includes(p);
const adminPages = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const pageLabel = computed(() => {
  const m = me.value?.menus.find((x) => x.code === page.value);
  return m ? (lang.value === "zh" ? m.name : m.nameEn) : "";
});
const selected = computed(() => detail.value?.visit);
const adminRows = computed(() =>
  rows.value.filter((r) =>
    JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
  ),
);
const adminVisible = computed(() =>
  adminRows.value.slice(offset.value * 20, offset.value * 20 + 20),
);
const icons = {
  visits: ClipboardList,
  workbench: CheckCircle2,
  onsite: Users,
  badges: ShieldCheck,
  dashboard: LayoutDashboard,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
  departments: Building2,
  menus: ClipboardList,
  permissions: ShieldCheck,
  dictionaries: ClipboardList,
  settings: Settings,
};
const departmentName = (id) =>
  options.value.departments.find((r) => r.id === id)?.name || "#" + id;
const hostName = (id) =>
  options.value.hosts.find((r) => r.id === id)?.name ||
  (id === me.value?.id ? me.value.displayName : "#" + id);
const field = (key, type = "text", extra = {}) => ({ key, type, ...extra });
const departmentChoices = () =>
  options.value.departments
    .filter((d) => me.value.scope === "ALL" || d.id === me.value.departmentId)
    .map((d) => ({ value: d.id, label: d.name }));
const roleChoices = ref([]),
  permissionChoices = ref([]);
const adminFields = {
  users: () => [
    field("username"),
    field("displayName"),
    field("password", "password", { required: !dialog.value?.id }),
    field("roleId", "select", { options: roleChoices.value }),
    field("departmentId", "select", { options: departmentChoices() }),
    field("enabled", "checkbox"),
  ],
  roles: () => [
    field("name"),
    field("scope", "select", {
      options: [
        { value: "ALL", label: tr("全部", "All") },
        {
          value: "DEPARTMENT",
          label: tr("本部门与本人来访", "Department and own visits"),
        },
        {
          value: "ASSIGNED",
          label: tr("本人／指定审批", "Own / assigned review"),
        },
      ],
    }),
    field("permissions", "permissions", { options: permissionChoices.value }),
  ],
  departments: () => [field("name")],
  menus: () => [
    field("code", "readonly"),
    field("name"),
    field("nameEn"),
    field("permissionCode", "select", { options: permissionChoices.value }),
    field("position", "number"),
    field("enabled", "checkbox"),
  ],
  permissions: () => [field("code", "readonly"), field("name")],
  dictionaries: () => [
    field("type"),
    field("code"),
    field("name"),
    field("nameEn"),
  ],
  settings: () => [field("code", "readonly"), field("value")],
};
/** 同源错误映射为业务反馈，失效会话返回登录页。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function fail(e) {
  error.value =
    pair(errors[e.message]) ||
    tr(
      "操作未完成，请检查输入后重试",
      "Operation failed. Check inputs and retry.",
    );
  if (e.message === "UNAUTHENTICATED") {
    me.value = null;
    detail.value = null;
    dialog.value = null;
  }
}
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  try {
    await api("/auth/logout", "POST");
  } catch (e) {
    fail(e);
  } finally {
    resetCsrf();
    me.value = null;
    detail.value = null;
    dialog.value = null;
  }
}
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("visitflow-language", lang.value);
}

/** 按当前岗位读取真实来访接口，失效权限不能继续访问详情。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    options.value = await api("/options");
    badges.value =
      can("badge.manage") || can("reception") ? await api("/badges") : [];
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "workbench";
    if (detail.value) {
      detail.value = await api("/visits/" + selected.value.id);
      return;
    }
    if (["visits", "onsite"].includes(page.value)) {
      const p = new URLSearchParams({
        search: search.value,
        status: status.value,
        sort: sort.value,
        page: offset.value,
        size: 20,
        mine: mine.value,
        onsite: page.value === "onsite",
      });
      const v = await api("/visits?" + p);
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else if (page.value === "audit") rows.value = await api("/audit");
    else if (adminPages.includes(page.value))
      rows.value = await api("/admin/" + page.value);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function navigate(p) {
  page.value = p;
  detail.value = null;
  search.value = "";
  status.value = "";
  offset.value = 0;
  success.value = "";
  await load();
}
async function openDetail(id) {
  error.value = "";
  try {
    detail.value = await api("/visits/" + id);
  } catch (e) {
    fail(e);
  }
}
/** 登记单只收集接待所需资料，选择启用的独立被访人。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function openVisit(v = null) {
  if (!options.value.hosts.length) {
    error.value = tr(
      "请先建立另一个具备接待确认权限的员工账号",
      "Create another active host account first.",
    );
    return;
  }
  const now = options.value.serverNow;
  dialog.value = {
    kind: "visit",
    id: v?.id,
    title: tr(
      v ? "编辑来访草稿" : "登记来访",
      v ? "Edit visit draft" : "New visit",
    ),
    form: v
      ? {
          ...v,
          startsAt: localInput(v.startsAt),
          endsAt: localInput(v.endsAt),
          requestKey: crypto.randomUUID(),
        }
      : {
          visitorName: "",
          organization: "",
          purpose: "",
          category: options.value.dictionaries[0]?.code,
          hostId: options.value.hosts[0].id,
          startsAt: localInput(now),
          endsAt: localInput(
            new Date(new Date(now).getTime() + 3600000).toISOString(),
          ),
          requestKey: crypto.randomUUID(),
        },
    fields: [
      field("visitorName"),
      field("organization"),
      field("hostId", "select", {
        options: options.value.hosts.map((h) => ({
          value: h.id,
          label: h.name + " · " + departmentName(h.departmentId),
        })),
      }),
      field("category", "select", {
        options: options.value.dictionaries.map((d) => ({
          value: d.code,
          label: lang.value === "zh" ? d.name : d.nameEn,
        })),
      }),
      field("startsAt", "datetime-local"),
      field("endsAt", "datetime-local"),
      field("purpose", "textarea"),
    ],
  };
  error.value = "";
}
function openCommand(action) {
  const v = selected.value;
  let fields = [
    field("note", "textarea", {
      required: ["reject", "cancel", "revoke", "deny", "check-out"].includes(
        action,
      ),
    }),
  ];
  if (action === "check-in") {
    fields = [
      field("badgeId", "select", {
        options: badges.value
          .filter(
            (b) =>
              b.badge.enabled &&
              !b.inUse &&
              b.badge.departmentId === v.departmentId,
          )
          .map((b) => ({
            value: b.badge.id,
            label: b.badge.code + " · " + b.badge.name,
          })),
      }),
      field("identityConfirmed", "checkbox"),
      ...fields,
    ];
  }
  if (action === "check-out")
    fields = [field("badgeReturned", "checkbox"), ...fields];
  dialog.value = {
    kind: "command",
    action,
    id: v.id,
    title: pair(commands[action]),
    form: {
      version: v.version,
      requestKey: crypto.randomUUID(),
      note: "",
      identityConfirmed: false,
      badgeReturned: false,
      badgeId: null,
    },
    fields,
  };
  error.value = "";
}
function openBadge(b = null) {
  dialog.value = {
    kind: "badge",
    id: b?.id,
    title: tr(b ? "编辑访客牌" : "建立访客牌", b ? "Edit badge" : "New badge"),
    form: b
      ? { ...b }
      : {
          code: "",
          name: "",
          departmentId: me.value.departmentId,
          enabled: true,
        },
    fields: [
      field("code"),
      field("name"),
      field("departmentId", "select", { options: departmentChoices() }),
      field("enabled", "checkbox"),
    ],
  };
  error.value = "";
}
async function openAdmin(row = null) {
  error.value = "";
  try {
    if (page.value === "users")
      roleChoices.value = (await api("/admin/roles")).map((r) => ({
        value: r.id,
        label: r.name,
      }));
    if (["roles", "menus"].includes(page.value))
      permissionChoices.value = (await api("/admin/permissions")).map((p) => ({
        value: p.code,
        label: p.name,
      }));
    dialog.value = {
      kind: "admin",
      id: row?.id,
      title: tr(
        row ? "编辑资料" : "新增资料",
        row ? "Edit record" : "New record",
      ),
      form: row
        ? {
            ...row,
            password: "",
            permissions: row.permissions ? [...row.permissions] : [],
          }
        : {
            name: "",
            nameEn: "",
            username: "",
            displayName: "",
            password: "",
            enabled: true,
            roleId: roleChoices.value[0]?.value,
            departmentId: me.value.departmentId,
            scope: "ASSIGNED",
            permissions: [],
            type: "visit",
            code: "",
          },
    };
    dialog.value.fields = adminFields[page.value]();
  } catch (e) {
    fail(e);
  }
}
function openPassword() {
  dialog.value = {
    kind: "password",
    title: tr("修改密码", "Change password"),
    form: { oldPassword: "", newPassword: "" },
    fields: [
      field("oldPassword", "password"),
      field("newPassword", "password"),
    ],
  };
}

/** 表单提交至真实接口，失败保留原内容及重试键，完成后刷新当前详情。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function saveDialog() {
  saving.value = true;
  error.value = "";
  try {
    const d = dialog.value;
    const f = { ...d.form };
    for (const x of d.fields) {
      if (x.type === "number") f[x.key] = Number(f[x.key]);
      if (x.type === "datetime-local") f[x.key] = instant(f[x.key]);
    }
    let result;
    if (d.kind === "visit")
      result = await api(
        "/visits" + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        f,
      );
    else if (d.kind === "command")
      result = await api(
        "/visits/" + d.id + "/commands/" + d.action,
        "POST",
        f,
      );
    else if (d.kind === "badge")
      await api("/badges" + (d.id ? "/" + d.id : ""), d.id ? "PUT" : "POST", f);
    else if (d.kind === "admin")
      await api(
        "/admin/" + page.value + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        f,
      );
    else if (d.kind === "password") {
      await api("/auth/password", "POST", f);
      me.value = null;
      resetCsrf();
    } else if (d.kind === "delete") {
      await api(d.path, "DELETE");
      detail.value = null;
    }
    dialog.value = null;
    success.value = tr("已保存", "Saved");
    if (result) await openDetail(result.id);
    if (me.value) await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
function openDelete(path) {
  dialog.value = {
    kind: "delete",
    path,
    title: tr("删除未使用资料", "Delete unused record"),
    form: {},
    fields: [],
  };
  error.value = "";
}
async function exportVisit() {
  try {
    const data = await api("/visits/" + selected.value.id + "/report.json");
    const u = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = u;
    a.download = "visit-" + selected.value.id + ".json";
    a.click();
    URL.revokeObjectURL(u);
  } catch (e) {
    fail(e);
  }
}
function eventLabel(e) {
  return (
    pair(commands[e.action.toLowerCase()]) ||
    pair(
      {
        CREATE: ["创建草稿", "Create draft"],
        SAVE: ["修改草稿", "Edit draft"],
        EXPIRED: ["未到访过期", "Visit expired"],
        DRAFT_DELETE: ["删除未提交草稿", "Delete draft"],
        BADGE_SAVE: ["管理访客牌", "Manage badge"],
        BADGE_DELETE: ["删除访客牌", "Delete badge"],
        LOGIN: ["登录", "Sign in"],
        PASSWORD_CHANGE: ["修改密码", "Password change"],
      }[e.action],
    ) ||
    tr("系统管理操作", "Administration")
  );
}
const genericColumns = computed(() => {
  if (page.value === "users")
    return ["username", "displayName", "roleId", "departmentId", "enabled"];
  if (page.value === "roles") return ["name", "scope", "permissions"];
  if (page.value === "menus")
    return ["code", "name", "permissionCode", "position", "enabled"];
  if (page.value === "permissions") return ["code", "name"];
  if (page.value === "settings") return ["code", "value"];
  if (page.value === "dictionaries") return ["type", "code", "name", "nameEn"];
  return ["name"];
});
function cell(row, key) {
  if (key === "departmentId") return departmentName(row[key]);
  if (key === "enabled")
    return tr(row[key] ? "启用" : "停用", row[key] ? "Enabled" : "Disabled");
  if (key === "permissions")
    return row[key]
      .map(
        (p) =>
          permissionChoices.value.find((v) => v.value === p)?.label ||
          {
            "visit.read": "查看来访",
            "visit.write": "来访登记",
            "visit.approve": "接待确认",
            "badge.manage": "访客牌",
            reception: "前台接待",
            dashboard: "来访统计",
            export: "数据导出",
            audit: "操作审计",
            admin: "系统管理",
          }[p] ||
          p,
      )
      .join(" · ");
  if (key === "scope")
    return pair(
      {
        ALL: ["全部", "All"],
        DEPARTMENT: ["本部门与本人来访", "Department / own visits"],
        ASSIGNED: ["本人及指定审批", "Own / assigned"],
      }[row[key]],
    );
  return row[key];
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <main v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span class="brand-sub"
        >VISITFLOW</span
      >
      <h1>
        {{ tr("企业访客", "Visitor") }}<br />{{
          tr("接待与在场管理", "reception & onsite register")
        }}
      </h1>
      <div class="brand-line"></div>
      <p class="small">
        {{
          tr(
            "公开源码学习版 · 非商业源码许可",
            "Learning source edition · Non-commercial license",
          )
        }}
      </p>
    </section>
    <section class="login-form">
      <form @submit.prevent="signIn">
        <span class="eyebrow">{{ tr("账号登录", "ACCOUNT SIGN IN") }}</span>
        <h2>{{ tr("欢迎回来", "Welcome back") }}</h2>
        <label
          >{{ tr("登录账号", "Username")
          }}<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ tr("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <button class="primary wide" :disabled="saving">
          {{
            tr(saving ? "登录中…" : "登录", saving ? "Signing in…" : "Sign in")
          }}
        </button>
      </form>
      <footer>
        <button @click="language">
          {{ lang === "zh" ? "English" : "中文" }}</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ tr("知华科技官网", "ZhuaTech website") }}
          <ArrowUpRight :size="13"
        /></a>
        <p>上海如静知华信息科技有限公司 · 微信 zhuatech / zhuatech2</p>
      </footer>
    </section>
  </main>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>VisitFlow</strong
          ><small>{{ tr("访客接待与在场管理", "Visitor reception") }}</small>
        </div>
      </div>
      <nav aria-label="主导航">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || ClipboardList" :size="18" /><span>{{
            lang === "zh" ? m.name : m.nameEn
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ tr("知华科技 · 商业咨询", "ZhuaTech · Business") }}
          <ArrowUpRight :size="13" /></a
        ><small>zhuatech / zhuatech2</small>
      </div>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb">VisitFlow <span>/</span> {{ pageLabel }}</div>
        <div class="account">
          <button @click="language">{{ lang === "zh" ? "EN" : "中文" }}</button
          ><button @click="openPassword">{{ me.displayName }}</button
          ><button :aria-label="tr('退出', 'Sign out')" @click="signOut">
            <LogOut :size="16" />
          </button>
        </div>
      </header>
      <div class="content">
        <div class="page-heading">
          <div>
            <span class="eyebrow">{{
              detail ? "VISIT DETAIL" : "VISITFLOW"
            }}</span>
            <h1>{{ detail ? selected.visitorName : pageLabel }}</h1>
            <p v-if="page === 'onsite' && !detail">
              {{
                tr("已入场且尚未登记离场", "Checked in and not yet checked out")
              }}
            </p>
          </div>
          <div class="heading-actions">
            <button :disabled="loading" @click="load">
              <RefreshCw :size="15" />{{ tr("刷新", "Refresh") }}</button
            ><button
              v-if="
                can('visit.write') &&
                !detail &&
                ['visits', 'workbench'].includes(page)
              "
              class="primary"
              @click="openVisit()"
            >
              <Plus :size="16" />{{ tr("登记来访", "New visit") }}</button
            ><button
              v-if="page === 'badges' && !detail"
              class="primary"
              @click="openBadge()"
            >
              <Plus :size="16" />{{ tr("建立访客牌", "New badge") }}</button
            ><button
              v-if="
                adminPages.includes(page) &&
                !['permissions', 'menus', 'settings'].includes(page)
              "
              class="primary"
              @click="openAdmin()"
            >
              <Plus :size="16" />{{ tr("新增", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error && !dialog" role="alert" class="error">{{ error }}</p>
        <p v-if="success && !dialog" role="status" class="success">
          {{ success }}
        </p>
        <p v-if="loading" class="muted">{{ tr("正在读取…", "Loading…") }}</p>
        <section v-if="detail" class="detail">
          <button
            class="back"
            @click="
              detail = null;
              load();
            "
          >
            <ChevronLeft :size="15" />{{ tr("返回列表", "Back to list") }}
          </button>
          <div class="detail-summary">
            <div>
              <span :class="['badge', selected.status]">{{
                pair(states[selected.status])
              }}</span>
              <h2>{{ selected.organization }}</h2>
              <p>
                {{ tr("被访人", "Host") }} · {{ detail.hostName }} ·
                {{ departmentName(selected.departmentId) }}
              </p>
            </div>
            <div>
              <strong>{{ date(selected.startsAt) }}</strong>
              <p>{{ tr("至", "to") }} {{ date(selected.endsAt) }}</p>
              <small
                >{{ tr("登记人", "Requester") }} · {{ detail.ownerName }}</small
              >
            </div>
          </div>
          <div class="action-row">
            <button v-if="editable(selected, me)" @click="openVisit(selected)">
              {{ tr("编辑草稿", "Edit draft") }}</button
            ><button
              v-for="action in actions(selected, me)"
              :key="action"
              :class="
                ['submit', 'approve', 'check-in'].includes(action)
                  ? 'primary'
                  : ''
              "
              @click="openCommand(action)"
            >
              {{ pair(commands[action]) }}</button
            ><button
              v-if="editable(selected, me) && !selected.submitted"
              @click="
                openDelete(
                  '/visits/' + selected.id + '?version=' + selected.version,
                )
              "
            >
              {{ tr("删除草稿", "Delete draft") }}</button
            ><button v-if="can('export')" @click="exportVisit">
              {{ tr("导出记录", "Export record") }}
            </button>
          </div>
          <div class="detail-grid">
            <section class="panel">
              <h3>{{ tr("来访与接待记录", "Visit and reception") }}</h3>
              <p class="preserve">{{ selected.purpose }}</p>
              <dl>
                <dt>{{ tr("访客牌", "Badge") }}</dt>
                <dd>{{ detail.badgeCode || "—" }}</dd>
                <dt>{{ tr("入场时间", "Check-in") }}</dt>
                <dd>{{ date(selected.checkedInAt) }}</dd>
                <dt>{{ tr("离场时间", "Check-out") }}</dt>
                <dd>{{ date(selected.checkedOutAt) }}</dd>
              </dl>
              <p
                v-if="
                  selected.status === 'IN_SITE' &&
                  new Date(selected.endsAt) <= new Date(options.serverNow)
                "
                class="error"
              >
                {{
                  tr("超过计划时间，仍在场", "Past planned end; still onsite")
                }}
              </p>
            </section>
            <section class="panel">
              <h3>{{ tr("接待历史", "Reception history") }}</h3>
              <ol class="event-list">
                <li v-for="e in detail.events" :key="e.id">
                  <div class="event-top">
                    <strong>{{ eventLabel(e) }}</strong
                    ><time>{{ date(e.createdAt) }}</time>
                  </div>
                  <small>{{
                    e.actor === "SYSTEM" ? tr("系统", "System") : e.actor
                  }}</small>
                  <p v-if="e.note">{{ e.note }}</p>
                  <details>
                    <summary>{{ tr("查看当时快照", "View snapshot") }}</summary>
                    <pre>{{
                      JSON.stringify(JSON.parse(e.snapshot), null, 2)
                    }}</pre>
                  </details>
                </li>
              </ol>
            </section>
          </div>
        </section>
        <section v-else-if="page === 'workbench'" class="work-grid">
          <div v-for="kind in ['reviews', 'mine']" :key="kind" class="panel">
            <h2>
              {{
                kind === "reviews"
                  ? tr("待我确认", "Awaiting my confirmation")
                  : tr("我登记的来访", "My visits")
              }}
              <small>{{ work[kind].length }}</small>
            </h2>
            <div v-for="v in work[kind]" :key="v.id" class="work-item">
              <div>
                <strong>{{ v.visitorName }}</strong>
                <p>{{ v.organization }} · {{ date(v.startsAt) }}</p>
                <span :class="['badge', v.status]">{{
                  pair(states[v.status])
                }}</span>
              </div>
              <button @click="openDetail(v.id)">
                {{ tr("处理", "Open") }}
              </button>
            </div>
            <p v-if="!work[kind].length" class="empty">
              {{ tr("暂无待处理事项", "Nothing to handle") }}
            </p>
          </div>
        </section>
        <section v-else-if="['visits', 'onsite'].includes(page)">
          <form
            class="filters"
            @submit.prevent="
              offset = 0;
              load();
            "
          >
            <label class="search"
              ><Search :size="16" /><input
                v-model="search"
                :placeholder="
                  tr('搜索来访人或单位', 'Search visitor or organization')
                " /></label
            ><select
              v-if="page === 'visits'"
              v-model="status"
              :aria-label="tr('状态筛选', 'State filter')"
              @change="
                offset = 0;
                load();
              "
            >
              <option value="">{{ tr("全部状态", "All states") }}</option>
              <option v-for="(value, key) in states" :key="key" :value="key">
                {{ pair(value) }}
              </option></select
            ><select
              v-model="sort"
              :aria-label="tr('排序', 'Sort')"
              @change="
                offset = 0;
                load();
              "
            >
              <option value="time">
                {{ tr("计划开始", "Planned start") }}
              </option>
              <option value="newest">{{ tr("最新登记", "Newest") }}</option>
              <option value="name">
                {{ tr("来访人姓名", "Visitor name") }}
              </option></select
            ><label v-if="page === 'visits'" class="check-inline"
              ><input
                v-model="mine"
                type="checkbox"
                @change="
                  offset = 0;
                  load();
                "
              />{{ tr("我登记的", "My requests") }}</label
            ><button>{{ tr("查询", "Search") }}</button>
          </form>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("来访人", "Visitor") }}</th>
                  <th>{{ tr("被访人及部门", "Host and department") }}</th>
                  <th>{{ tr("计划时间", "Planned time") }}</th>
                  <th>{{ tr("入场时间", "Check-in") }}</th>
                  <th>{{ tr("状态", "State") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="v in rows" :key="v.id">
                  <td>
                    <strong>{{ v.visitorName }}</strong
                    ><small>{{ v.organization }}</small>
                  </td>
                  <td>
                    {{ hostName(v.hostId)
                    }}<small>{{ departmentName(v.departmentId) }}</small>
                  </td>
                  <td>
                    {{ date(v.startsAt) }}<small>{{ date(v.endsAt) }}</small>
                  </td>
                  <td>{{ date(v.checkedInAt) }}</td>
                  <td>
                    <span :class="['badge', v.status]">{{
                      pair(states[v.status])
                    }}</span
                    ><small
                      v-if="
                        v.status === 'IN_SITE' &&
                        new Date(v.endsAt) <= new Date(options.serverNow)
                      "
                      class="error"
                      >{{ tr("超计划时间", "Overdue") }}</small
                    >
                  </td>
                  <td>
                    <button @click="openDetail(v.id)">
                      {{ tr("查看", "View") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无匹配来访", "No matching visits") }}
            </p>
          </div>
          <div class="pagination">
            <span>{{ tr("共", "Total") }} {{ total }}</span
            ><button
              :disabled="offset === 0"
              @click="
                offset--;
                load();
              "
            >
              <ChevronLeft :size="15" /></button
            ><span>{{ offset + 1 }}</span
            ><button
              :disabled="(offset + 1) * 20 >= total"
              @click="
                offset++;
                load();
              "
            >
              <ChevronRight :size="15" />
            </button>
          </div>
        </section>
        <section v-else-if="page === 'badges'">
          <div class="filters">
            <label class="search"
              ><Search :size="16" /><input
                v-model="search"
                :placeholder="tr('搜索访客牌', 'Search badges')"
            /></label>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("编号与名称", "Code and name") }}</th>
                  <th>{{ tr("部门", "Department") }}</th>
                  <th>{{ tr("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="b in badges.filter((b) =>
                    [b.badge.code, b.badge.name]
                      .join(' ')
                      .toLowerCase()
                      .includes(search.toLowerCase()),
                  )"
                  :key="b.badge.id"
                >
                  <td>
                    <strong>{{ b.badge.code }}</strong
                    ><small>{{ b.badge.name }}</small>
                  </td>
                  <td>{{ departmentName(b.badge.departmentId) }}</td>
                  <td>
                    {{
                      b.inUse
                        ? tr("已发放", "Issued")
                        : b.badge.enabled
                          ? tr("可发放", "Available")
                          : tr("停用", "Disabled")
                    }}
                  </td>
                  <td>
                    <button @click="openBadge(b.badge)">
                      {{ tr("编辑", "Edit") }}</button
                    ><button
                      v-if="!b.inUse"
                      @click="
                        openDelete(
                          '/badges/' +
                            b.badge.id +
                            '?version=' +
                            b.badge.version,
                        )
                      "
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!badges.length" class="empty">
              {{ tr("暂无访客牌", "No visitor badges") }}
            </p>
          </div>
        </section>
        <section v-else-if="page === 'dashboard'">
          <div class="metrics">
            <article
              v-for="[key, zh, en] in [
                ['total', '授权来访', 'Visible visits'],
                ['pending', '待确认', 'Pending'],
                ['onsite', '仍在场', 'Onsite'],
                ['overdue', '在场逾时', 'Overdue onsite'],
              ]"
              :key="key"
            >
              <span>{{ tr(zh, en) }}</span
              ><strong>{{ stats[key] || 0 }}</strong>
            </article>
          </div>
          <div class="panel">
            <h2>{{ tr("来访状态分布", "Visits by state") }}</h2>
            <div
              v-for="(value, key) in stats.states"
              :key="key"
              class="stat-row"
            >
              <strong>{{ pair(states[key]) }}</strong>
              <div class="bar">
                <span
                  :style="{
                    width: (value / Math.max(1, stats.total)) * 100 + '%',
                  }"
                ></span>
              </div>
              <span>{{ value }}</span>
            </div>
            <p class="small">
              {{ tr("已完成接待时间", "Completed reception time") }} ·
              {{ stats.minutes || 0 }} min
            </p>
          </div>
        </section>
        <section v-else-if="page === 'audit'">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("时间", "Time") }}</th>
                  <th>{{ tr("操作人", "Actor") }}</th>
                  <th>{{ tr("动作", "Action") }}</th>
                  <th>{{ tr("记录编号", "Record") }}</th>
                  <th>{{ tr("部门", "Department") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in rows.slice().reverse()" :key="e.id">
                  <td>{{ date(e.createdAt) }}</td>
                  <td>{{ e.actor }}</td>
                  <td>{{ eventLabel(e) }}</td>
                  <td>{{ e.objectId }}</td>
                  <td>{{ departmentName(e.departmentId) }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无记录", "No records") }}
            </p>
          </div>
        </section>
        <section v-else-if="adminPages.includes(page)">
          <div class="filters">
            <label class="search"
              ><Search :size="16" /><input
                v-model="search"
                :placeholder="tr('搜索资料', 'Search records')"
                @input="offset = 0"
            /></label>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th v-for="key in genericColumns" :key="key">
                    {{ pair(labels[key]) }}
                  </th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in adminVisible" :key="row.id">
                  <td v-for="key in genericColumns" :key="key">
                    {{ cell(row, key) }}
                  </td>
                  <td>
                    <button @click="openAdmin(row)">
                      {{ tr("编辑", "Edit") }}</button
                    ><button
                      v-if="
                        !['menus', 'permissions', 'settings'].includes(page)
                      "
                      @click="openDelete('/admin/' + page + '/' + row.id)"
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span>{{ tr("共", "Total") }} {{ adminRows.length }}</span
            ><button :disabled="offset === 0" @click="offset--">
              <ChevronLeft :size="15" /></button
            ><span>{{ offset + 1 }}</span
            ><button
              :disabled="(offset + 1) * 20 >= adminRows.length"
              @click="offset++"
            >
              <ChevronRight :size="15" />
            </button>
          </div>
        </section>
        <footer class="app-footer">
          <span>© 2026 上海如静知华信息科技有限公司</span
          ><span
            >VisitFlow 0.1.0 ·
            {{
              tr(
                "公开源码学习版／非商业源码版，未经书面授权不得商用",
                "Learning source edition / non-commercial; written permission required for commercial use",
              )
            }}</span
          >
        </footer>
      </div>
    </section>
  </div>
  <div
    v-if="dialog"
    class="modal-backdrop"
    @click.self="!saving && (dialog = null)"
  >
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.title"
    >
      <header>
        <h2>{{ dialog.title }}</h2>
        <button
          :disabled="saving"
          :aria-label="tr('关闭', 'Close')"
          @click="dialog = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="saveDialog">
        <p v-if="dialog.kind === 'delete'" class="muted">
          {{
            tr(
              "仅可删除没有业务引用的资料或未提交草稿。此操作会删除该记录。",
              "Only unused records or never-submitted drafts may be deleted. This removes the record.",
            )
          }}
        </p>
        <div class="form-grid">
          <label
            v-for="f in dialog.fields"
            :key="f.key"
            :class="{
              full: f.type === 'textarea' || f.type === 'permissions',
              'check-inline': f.type === 'checkbox',
            }"
            ><span>{{ pair(labels[f.key]) }}</span
            ><template v-if="f.type === 'permissions'"
              ><div class="permission-options">
                <label
                  v-for="p in f.options"
                  :key="p.value"
                  class="check-inline"
                  ><input
                    v-model="dialog.form[f.key]"
                    type="checkbox"
                    :value="p.value"
                  />{{ p.label }}</label
                >
              </div></template
            ><textarea
              v-else-if="f.type === 'textarea'"
              v-model="dialog.form[f.key]"
              :required="f.required !== false"
              :maxlength="f.key === 'purpose' ? 2000 : 1000"
              rows="3"
            ></textarea
            ><select
              v-else-if="f.type === 'select'"
              v-model="dialog.form[f.key]"
              :disabled="f.disabled"
              :required="f.required !== false"
            >
              <option v-if="f.key === 'badgeId'" :value="null" disabled>
                {{ tr("请选择访客牌", "Choose a visitor badge") }}
              </option>
              <option v-for="o in f.options" :key="o.value" :value="o.value">
                {{ o.label }}
              </option></select
            ><input
              v-else-if="f.type === 'checkbox'"
              v-model="dialog.form[f.key]"
              type="checkbox" />
            <input
              v-else
              v-model="dialog.form[f.key]"
              :type="f.type === 'readonly' ? 'text' : f.type"
              :readonly="f.type === 'readonly'"
              :required="f.required !== false && f.type !== 'readonly'"
              :min="f.min"
              :max="f.max"
              :step="f.type === 'datetime-local' ? 60 : f.step || 1"
              :maxlength="
                ['password', 'oldPassword', 'newPassword'].includes(f.key)
                  ? 128
                  : f.key === 'organization'
                    ? 600
                    : f.key === 'code'
                      ? 60
                      : 120
              "
              :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
          /></label>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" :disabled="saving" @click="dialog = null">
            {{ tr("取消", "Cancel") }}</button
          ><button class="primary" :disabled="saving">
            {{
              tr(saving ? "保存中…" : "确认", saving ? "Saving…" : "Confirm")
            }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
