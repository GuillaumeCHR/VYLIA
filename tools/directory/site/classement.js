// Tris et filtres du classement. Sans dépendance ; sans ce script, la page reste lisible (classement par score).
(function () {
  "use strict";
  var table = document.getElementById("ranking");
  if (!table) return;
  var tbody = table.tBodies[0];
  var rows = Array.prototype.slice.call(tbody.rows);
  var q = document.getElementById("q");
  var cat = document.getElementById("cat");
  var badge = document.getElementById("badge");
  var count = document.getElementById("count");
  var chips = Array.prototype.slice.call(document.querySelectorAll(".chip"));
  var collator = new Intl.Collator(document.documentElement.lang || "fr", { sensitivity: "base" });

  function pressed(kind) {
    return chips.filter(function (c) { return c.dataset.filter === kind && c.getAttribute("aria-pressed") === "true"; })
      .map(function (c) { return c.dataset.value; });
  }

  function apply() {
    var text = q.value.trim().toLowerCase();
    var sections = pressed("section");
    var grades = pressed("grade");
    var shown = 0;
    rows.forEach(function (r) {
      var ok = sections.indexOf(r.dataset.section) >= 0 &&
        (r.dataset.grade === "-" || grades.indexOf(r.dataset.grade) >= 0) &&
        (!cat.value || r.dataset.cat === cat.value) &&
        (!badge || !badge.value || (" " + r.dataset.badges + " ").indexOf(" " + badge.value + " ") >= 0) &&
        (!text || r.dataset.name.indexOf(text) >= 0);
      r.hidden = !ok;
      if (ok) shown++;
    });
    count.textContent = (count.dataset.tpl || "{n} / {total}").replace("{n}", shown).replace("{total}", rows.length);
  }

  var headers = Array.prototype.slice.call(table.tHead.rows[0].cells);
  headers.forEach(function (th) {
    var key = th.dataset.sort;
    if (!key) return;
    var button = document.createElement("button");
    button.type = "button";
    while (th.firstChild) button.appendChild(th.firstChild);
    th.appendChild(button);
    button.addEventListener("click", function () {
      var text = th.dataset.type === "text";
      var current = th.getAttribute("aria-sort");
      var dir = current === "descending" ? "ascending" : current === "ascending" ? "descending" : (text ? "ascending" : "descending");
      headers.forEach(function (h) { h.removeAttribute("aria-sort"); });
      th.setAttribute("aria-sort", dir);
      var sign = dir === "ascending" ? 1 : -1;
      rows.sort(function (a, b) {
        var x = a.dataset[key], y = b.dataset[key];
        var c = text ? collator.compare(x, y) : (parseFloat(x) - parseFloat(y));
        return c * sign || collator.compare(a.dataset.name, b.dataset.name);
      });
      rows.forEach(function (r) { tbody.appendChild(r); });
    });
  });

  chips.forEach(function (c) {
    c.addEventListener("click", function () {
      c.setAttribute("aria-pressed", c.getAttribute("aria-pressed") === "true" ? "false" : "true");
      apply();
    });
  });
  q.addEventListener("input", apply);
  cat.addEventListener("change", apply);
  if (badge) badge.addEventListener("change", apply);
  apply();
})();
