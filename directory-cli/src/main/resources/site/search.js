// Annuaire VYLIA : recherche, filtres et tri du tableau. Sans ce script, le tableau complet reste lisible.
(function () {
  var table = document.getElementById("apps");
  if (!table) return;
  var body = table.tBodies[0];
  var rows = Array.prototype.slice.call(body.rows);
  var q = document.getElementById("q"), category = document.getElementById("category"),
      grade = document.getElementById("grade"), country = document.getElementById("country"),
      count = document.getElementById("count");
  function norm(s) { return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase(); }
  function apply() {
    var text = norm(q.value.trim()), shown = 0;
    rows.forEach(function (r) {
      var ok = (!text || norm(r.dataset.name).indexOf(text) >= 0) &&
        (!category.value || r.dataset.category === category.value) &&
        (!grade.value || r.dataset.grade === grade.value) &&
        (!country.value || (" " + r.dataset.countries + " ").indexOf(" " + country.value + " ") >= 0);
      r.hidden = !ok;
      if (ok) shown++;
    });
    count.textContent = shown + " app" + (shown > 1 ? "s" : "") + " affichée" + (shown > 1 ? "s" : "");
  }
  var asc = {};
  Array.prototype.forEach.call(table.querySelectorAll("th button"), function (b) {
    b.addEventListener("click", function () {
      var key = b.dataset.sort; asc[key] = !asc[key];
      rows.sort(function (x, y) {
        var a = key === "score" ? Number(x.dataset.score) : x.dataset.name;
        var c = key === "score" ? Number(y.dataset.score) : y.dataset.name;
        return (a < c ? -1 : a > c ? 1 : 0) * (asc[key] ? 1 : -1);
      });
      rows.forEach(function (r) { body.appendChild(r); });
    });
  });
  [q, category, grade, country].forEach(function (el) { el.addEventListener("input", apply); });
  document.querySelector(".filters").addEventListener("submit", function (e) { e.preventDefault(); });
  apply();
})();
