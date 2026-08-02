/*
 * Rhino has no console. Highlight.js logs through it while registering some
 * grammars, which would abort loading with a ReferenceError. Feature-detected,
 * so a host that does provide console keeps its own.
 */
var console = typeof console === "undefined" ? {
  log: function () {}, warn: function () {}, error: function () {},
  info: function () {}, debug: function () {}
} : console;
