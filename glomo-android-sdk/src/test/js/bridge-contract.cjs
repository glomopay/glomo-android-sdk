// Run with Node.js; exercises the actual scripts embedded in the Kotlin source.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../../main/java/com/glomopay/sdk/android/bridge/GlomoPayInjectionScripts.kt'), 'utf8');
function script(name, bridge) {
  const start = source.indexOf('fun ' + name + '(');
  assert.ok(start >= 0);
  const text = source.slice(start).match(/"""([\s\S]*?)"""/)[1];
  return text.replaceAll('${bridgeName}', bridge).replaceAll('$bridgeName', bridge);
}
function environment(hasBodyContent = true) {
  const messages = [], timers = [], listeners = {};
  const window = {
    postMessage(data) { listeners.message?.({data}); },
    addEventListener(name, fn) { listeners[name] = fn; },
    GlomoCarousel: {postMessage(raw) { messages.push(JSON.parse(raw)); }},
    GlomoPayFlowBridge: {postMessage(raw) { messages.push(JSON.parse(raw)); }},
    close() {},
  };
  window.top = window;
  const context = vm.createContext({window, console: {log(){},warn(){},error(){},info(){}},
    document: {
      readyState: 'complete',
      addEventListener(){},
      body: {innerText: hasBodyContent ? 'x'.repeat(101) : '', querySelectorAll(){return [];}},
    },
    HTMLFormElement: function(){}, XMLHttpRequest: function(){},
    setTimeout(fn, delay) { assert.equal(delay, 3000); timers.push(fn); },
  });
  context.HTMLFormElement.prototype.submit = function() {};
  context.XMLHttpRequest.prototype.open = function() {};
  return {context, window, messages, timers, listeners};
}
for (const value of [true, false]) {
  const e = environment();
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  e.window.postMessage({event:'lrs.has_education_steps', hasContent:value});
  assert.ok(e.messages.length > 0);
  assert.ok(e.messages.every(m => m.hasContent === value));
  vm.runInContext(script('carouselFallback'), e.context);
  const count = e.messages.length;
  e.timers[0]();
  assert.equal(e.messages.length, count, 'fallback must respect explicit availability');
}
for (const content of [true, false]) {
  const e = environment(content);
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  vm.runInContext(script('carouselFallback'), e.context);
  e.timers[0]();
  assert.equal(e.messages[0].hasContent, content);
}
const flow = environment();
vm.runInContext(script('build', 'GlomoPayFlowBridge') + script('flow', 'GlomoPayFlowBridge'), flow.context);
flow.window.opener.postMessage({type:'payment.pending'});
flow.window.opener.postMessage(JSON.stringify({type:'payment.cancelled'}));
assert.deepEqual(flow.messages.slice(-2), [
  {type:'message',data:{type:'payment.pending'}},
  {type:'message',data:{type:'payment.cancelled'}},
]);
assert.equal(flow.messages.filter(m => m.type === 'bridge.ready').length, 1);
vm.runInContext(script('build', 'GlomoPayFlowBridge'), flow.context);
assert.equal(flow.messages.filter(m => m.type === 'bridge.ready').length, 1, 'bridge injection must be idempotent');
const deferred = environment();
deferred.context.document.readyState = 'loading';
vm.runInContext(script('build', 'GlomoPayFlowBridge'), deferred.context);
assert.equal(
  deferred.messages.filter(m => m.type === 'bridge.ready').length,
  0,
  'bridge.ready must not fire before the document has loaded',
);
deferred.listeners.load();
assert.equal(
  deferred.messages.filter(m => m.type === 'bridge.ready').length,
  1,
  'bridge.ready fires once, on load',
);
deferred.listeners.load();
assert.equal(
  deferred.messages.filter(m => m.type === 'bridge.ready').length,
  1,
  'bridge.ready load handler must be idempotent',
);
const subframe = environment();
subframe.context.window.top = {};
vm.runInContext(script('build', 'GlomoPayFlowBridge'), subframe.context);
assert.equal(
  subframe.messages.filter(m => m.type === 'bridge.ready').length,
  0,
  'subframes must not emit bridge.ready',
);
const viewport = environment();
const meta = {setAttribute(name, value) { this[name] = value; }};
viewport.context.document.head = {};
viewport.context.document.querySelector = () => meta;
viewport.context.document.documentElement = {style:{}};
viewport.context.document.body.style = {};
vm.runInContext(script('bankViewportFit'), viewport.context);
assert.ok(meta.content.includes('width=device-width'));
assert.ok(meta.content.includes('initial-scale=1'));
assert.equal(viewport.context.document.body.style.zoom, '1');
vm.runInContext(script('bankViewportFit'), viewport.context);
console.log('PASS: carousel true/false, DOM fallback, explicit-message precedence, flow opener, bridge readiness/idempotence, and bank viewport normalization');
