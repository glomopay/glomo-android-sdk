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
    setTimeout(fn, delay) { timers.push({fn, delay}); },
  });
  context.HTMLFormElement.prototype.submit = function() {};
  context.XMLHttpRequest.prototype.open = function() {};
  return {context, window, messages, timers, listeners};
}
// The live page's exact signal (glomopay-checkout lrs-carousel.event-emitter.ts). It is the only
// message that shows the carousel; the page never emits value:false.
const LIVE_SIGNAL = {type: 'lrs.has_education_steps', value: true};
const carouselMessages = (e) => e.messages.filter(m => m.type === 'lrs.has_education_steps');

for (const message of [LIVE_SIGNAL, JSON.stringify(LIVE_SIGNAL)]) {
  const e = environment();
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  e.window.postMessage(message);
  assert.deepEqual(carouselMessages(e), [LIVE_SIGNAL], 'the live signal is forwarded exactly once');
}

for (const [label, message] of [
  ['value false', {type: 'lrs.has_education_steps', value: false}],
  ['event/hasContent shape', {event: 'lrs.has_education_steps', hasContent: true}],
  ['string value', {type: 'lrs.has_education_steps', value: 'true'}],
  ['missing value', {type: 'lrs.has_education_steps'}],
  ['unrelated type', {type: 'payment.pending', value: true}],
  ['malformed JSON', '{"type":"lrs.has_education_steps",'],
  ['plain text', 'hello'],
]) {
  const e = environment();
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  e.window.postMessage(message);
  assert.equal(carouselMessages(e).length, 0, label + ' must not show the carousel');
}

{
  // No signal at all: nothing is forwarded, and there is no timer-driven DOM heuristic.
  const e = environment();
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  assert.equal(carouselMessages(e).length, 0);
  assert.equal(e.timers.length, 0, 'the carousel script schedules nothing');
  assert.ok(!source.includes('fun carouselFallback('), 'the DOM fallback is gone');
}

{
  // The page signals before the native bridge exists (the document-start listener catches it);
  // the next injection, at page start or finish, delivers it once.
  const e = environment();
  const peer = e.window.GlomoCarousel;
  delete e.window.GlomoCarousel;
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  e.window.postMessage(LIVE_SIGNAL);
  assert.equal(carouselMessages(e).length, 0);
  e.window.GlomoCarousel = peer;
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  assert.deepEqual(carouselMessages(e), [LIVE_SIGNAL], 'an early signal is delivered once the bridge is ready');
  vm.runInContext(script('carousel', 'GlomoCarousel'), e.context);
  assert.equal(carouselMessages(e).length, 1, 'and only once');
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
// bridge() and the page's error listeners. dispatch() models the worst case the task describes:
// an exception escaping a listener is reported as a fresh 'error' event. The cap turns a loop
// into a failed assertion instead of a hang.
function withPeer(postMessage) {
  const e = environment();
  e.warnings = [];
  e.context.console.warn = (m) => e.warnings.push(String(m));
  e.window.GlomoPayFlowBridge = {postMessage};
  e.redispatched = 0;
  e.dispatch = (name, event) => {
    try {
      e.listeners[name]?.(event);
    } catch (err) {
      if (++e.redispatched > 50) throw new Error('error listener loop');
      e.dispatch('error', {message: String(err && err.message)});
    }
  };
  vm.runInContext(script('build', 'GlomoPayFlowBridge'), e.context);
  return e;
}
const pageErrors = (msgs, kind) => msgs.filter(m => m.type === 'webview.error' && m.errorType === kind);

{
  // A dead peer: every call throws. Bounded calls, no re-dispatch, recorded once.
  let calls = 0;
  const e = withPeer(() => { calls++; throw new Error('peer gone'); });
  const afterLoad = calls;
  e.dispatch('error', {message: 'page boom'});
  e.dispatch('unhandledrejection', {reason: 'page rejection'});
  assert.equal(calls, afterLoad + 2, 'one bridge call per page error, no retries');
  assert.equal(e.redispatched, 0, 'a bridge failure must never reach the error listener');
  assert.equal(e.warnings.filter(w => w.includes('postMessage failed')).length, 1, 'recorded once per page');
  assert.equal(e.window.__glomo_GlomoPayFlowBridge_Failed__, true);
}

{
  // A peer that raises an error event synchronously from inside its own postMessage, then
  // throws: without the re-entrancy guard this recurses without bound.
  let calls = 0;
  let e;
  e = withPeer(() => {
    calls++;
    if (e) e.dispatch('error', {message: 'raised by the bridge'});
    throw new Error('peer gone');
  });
  const afterLoad = calls;
  e.dispatch('error', {message: 'page boom'});
  assert.equal(calls, afterLoad + 1, 'the bridge must not re-enter itself');
  assert.equal(e.redispatched, 0);
}

{
  // Healthy peer: every page error is still reported one-for-one, nothing is warned.
  const messages = [];
  const e = withPeer((raw) => messages.push(JSON.parse(raw)));
  for (const m of ['a', 'b', 'c']) e.dispatch('error', {message: m});
  for (const r of ['x', 'y']) e.dispatch('unhandledrejection', {reason: r});
  assert.deepEqual(pageErrors(messages, 'js_error').map(m => m.message), ['a', 'b', 'c']);
  assert.deepEqual(pageErrors(messages, 'unhandled_rejection').map(m => m.message), ['x', 'y']);
  assert.equal(messages.filter(m => m.type === 'bridge.ready').length, 1);
  assert.equal(e.warnings.length, 0);
  assert.equal(e.window.__glomo_GlomoPayFlowBridge_Failed__, undefined);
}

{
  // A peer that fails once and recovers: later page errors are not swallowed.
  const messages = [];
  let failNext = true;
  const e = withPeer((raw) => {
    if (failNext) { failNext = false; throw new Error('transient'); }
    messages.push(JSON.parse(raw));
  });
  e.dispatch('error', {message: 'after recovery'});
  assert.deepEqual(pageErrors(messages, 'js_error').map(m => m.message), ['after recovery']);
}

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
console.log('PASS: carousel live signal, rejected shapes, no-signal, early signal, bridge failure containment, page errors one-for-one, flow opener, bridge readiness/idempotence, and bank viewport normalization');
