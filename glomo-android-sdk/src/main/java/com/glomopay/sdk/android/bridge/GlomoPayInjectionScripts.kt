package com.glomopay.sdk.android.bridge

/** Payment-event bridge script mirrored from the Flutter InjectionScripts. */
internal object GlomoPayInjectionScripts {
    fun main(bridgeName: String = "GlomoPayBridge"): String = build(bridgeName)

    fun flow(bridgeName: String = "GlomoPayFlowBridge"): String = build(bridgeName)

    fun carousel(bridgeName: String = "GlomoCarousel"): String = """
        (function() {
          var pendingKey = '__glomoCarouselPendingMessage__';
          var send = function(data) {
            try {
              var parsed = typeof data === 'string' ? JSON.parse(data) : data;
              if (!parsed) return;
              if (parsed.type !== 'lrs.has_education_steps' || parsed.value !== true) return;
              var payload = JSON.stringify({type:parsed.type,value:true});
              if (window.$bridgeName) window.$bridgeName.postMessage(payload);
              else window[pendingKey] = payload;
            } catch(e) {}
          };

          if (!window.__glomoCarouselListenerReady__) {
            window.__glomoCarouselListenerReady__ = true;
            try {
              var originalPostMessage = window.postMessage.bind(window);
              window.postMessage = function(message, targetOrigin, transfer) {
                send(message);
                return originalPostMessage(message, targetOrigin, transfer);
              };
            } catch(e) {}
            window.addEventListener('message', function(event) {
              if (event.data) send(event.data);
            });
          }

          if (window[pendingKey] && window.$bridgeName) {
            window.$bridgeName.postMessage(window[pendingKey]);
            window[pendingKey] = null;
          }
        })();
    """.trimIndent()

    private fun build(bridgeName: String): String = """
        (function() {
          var flag = '__glomo_${bridgeName}_Injected__';
          if (window[flag]) return;
          window[flag] = true;
          var bridge = function(msg) {
            if (window.$bridgeName) window.$bridgeName.postMessage(msg);
          };
          var dev = function() { return window.__glomoDevMode__ === true; };
          bridge(JSON.stringify({type:'console',level:'info',message:'GlomoPay Injection Loaded ($bridgeName)'}));

          var oldLog = console.log, oldWarn = console.warn;
          var oldError = console.error, oldInfo = console.info;
          var sendLog = function(level, message) {
            if (dev()) bridge(JSON.stringify({type:'console',level:level,message:String(message)}));
          };
          console.log = function(m) { oldLog(m); sendLog('log', m); };
          console.warn = function(m) { oldWarn(m); sendLog('warn', m); };
          console.error = function(m) { oldError(m); sendLog('error', m); };
          console.info = function(m) { oldInfo(m); sendLog('info', m); };

          window.open = function(url) {
            if (url) bridge(JSON.stringify({type:'window.open',url:String(url)}));
            return {
              close:function(){ bridge(JSON.stringify({type:'window.close'})); },
              focus:function(){}, blur:function(){}, postMessage:function(){},
              location: {
                get href(){ return url || ''; },
                set href(v){ bridge(JSON.stringify({type:'window.open',url:String(v)})); },
                assign:function(v){ bridge(JSON.stringify({type:'window.open',url:String(v)})); },
                replace:function(v){ bridge(JSON.stringify({type:'window.open',url:String(v)})); }
              }
            };
          };
          var oldClose = window.close;
          window.close = function() {
            bridge(JSON.stringify({type:'window.close'}));
            try { oldClose(); } catch(e) {}
          };

          try {
            var oldSubmit = HTMLFormElement.prototype.submit;
            HTMLFormElement.prototype.submit = function() {
              if (this.target === '_blank') this.target = '_self';
              return oldSubmit.call(this);
            };
          } catch(e) {}

          // Mirror Flutter's dev-only network diagnostics without inspecting
          // response bodies for payment decisions.
          var originalFetch = window.fetch;
          if (originalFetch) {
            window.fetch = function() {
              var args = arguments;
              var requestUrl = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '';
              var noisy = requestUrl.indexOf('.lottie') >= 0 || requestUrl.indexOf('.wasm') >= 0;
              if (dev() && !noisy) sendLog('info', 'Fetch Start: ' + requestUrl);
              return originalFetch.apply(this, args).then(function(response) {
                if (dev() && !noisy) sendLog('info', 'Fetch Complete: ' + requestUrl + ' | HTTP ' + response.status);
                return response;
              }).catch(function(error) {
                if (dev() && !noisy) sendLog('error', 'Fetch Error: ' + error);
                throw error;
              });
            };
          }

          var originalXhrOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(method, url) {
            this.__glomoUrl = url;
            this.__glomoMethod = method;
            if (dev() && url && url.indexOf('.lottie') < 0 && url.indexOf('.wasm') < 0) {
              sendLog('info', 'XHR Start: ' + method + ' ' + url);
            }
            return originalXhrOpen.apply(this, arguments);
          };

          window.addEventListener('error', function(e) {
            bridge(JSON.stringify({type:'webview.error',errorType:'js_error',message:String(e.message||'JavaScript error')}));
            sendLog('error', 'Uncaught: ' + e.message);
          });
          window.addEventListener('unhandledrejection', function(e) {
            bridge(JSON.stringify({type:'webview.error',errorType:'unhandled_rejection',message:String(e.reason||'Unhandled promise rejection')}));
            sendLog('error', 'Unhandled Rejection: ' + e.reason);
          });
          window.addEventListener('message', function(e) {
            if (e.data) bridge(JSON.stringify({type:'message',data:e.data}));
          });
          document.addEventListener('click', function(e) {
            var t = e.target;
            if (t && t.tagName === 'INPUT' && t.type === 'file') {
              bridge(JSON.stringify({type:'file.input',accept:t.getAttribute('accept')||'',
                capture:t.getAttribute('capture')||'',inputId:t.id||'',inputName:t.name||''}));
            }
          }, true);
        })();
    """.trimIndent()
}
