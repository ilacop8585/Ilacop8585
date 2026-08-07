package com.nexus.universalbridge.finalapp;

import org.json.JSONObject;

public final class WebAutomation {
    private WebAutomation() {}

    private static String q(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    public static String scriptFor(String type, JSONObject payload) {
        String selector = payload == null ? "" : payload.optString("selector", "");
        if (selector.length() > 500) return result(false, "selector_too_long");
        String sensitive = SecurityRules.SENSITIVE_REGEX_JS;

        switch (type) {
            case "click":
                return "(function(){try{const e=document.querySelector(" + q(selector) + ");if(!e)return JSON.stringify({ok:false,error:'not_found'});e.scrollIntoView({block:'center'});e.click();return JSON.stringify({ok:true});}catch(x){return JSON.stringify({ok:false,error:'js_error'});}})();";
            case "type": {
                String text = payload.optString("text", "");
                return "(function(){try{const e=document.querySelector(" + q(selector) + ");if(!e)return JSON.stringify({ok:false,error:'not_found'});const t=(e.type||'').toLowerCase(),a=(e.autocomplete||'').toLowerCase(),m=[e.id,e.name,e.getAttribute('aria-label'),e.placeholder].filter(Boolean).join(' ');if(t==='password'||a.includes('current-password')||a.includes('new-password')||a.includes('one-time-code')||/" + sensitive + "/i.test(m))return JSON.stringify({ok:false,error:'sensitive_field_human_only'});if(!('value' in e))return JSON.stringify({ok:false,error:'not_editable'});e.focus();e.value=" + q(text) + ";e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({ok:true});}catch(x){return JSON.stringify({ok:false,error:'js_error'});}})();";
            }
            case "select": {
                String value = payload.optString("value", "");
                return "(function(){try{const e=document.querySelector(" + q(selector) + ");if(!e)return JSON.stringify({ok:false,error:'not_found'});if(e.tagName!=='SELECT')return JSON.stringify({ok:false,error:'not_select'});e.value=" + q(value) + ";e.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({ok:true});}catch(x){return JSON.stringify({ok:false,error:'js_error'});}})();";
            }
            case "scroll": {
                int x = payload.optInt("x", 0);
                int y = payload.optInt("y", 600);
                return "(function(){window.scrollBy(" + x + "," + y + ");return JSON.stringify({ok:true,x:window.scrollX,y:window.scrollY});})();";
            }
            case "read":
                return snapshotScript();
            case "finish": {
                String expected = payload.optString("expectedText", "");
                return "(function(){const matched=(document.body&&document.body.innerText||'').includes(" + q(expected) + ");return JSON.stringify({ok:true,matched:matched,url:location.href,title:document.title});})();";
            }
            default:
                return result(false, "unsupported_action");
        }
    }

    public static String snapshotScript() {
        String sensitive = SecurityRules.SENSITIVE_REGEX_JS;
        return "(function(){try{const rx=/" + sensitive + "/i;const esc=(s)=>{try{return CSS.escape(s)}catch(e){return String(s).replace(/[^a-zA-Z0-9_-]/g,'\\\\$&')}};const sel=(e)=>{if(e.id)return '#'+esc(e.id);let p=e.tagName.toLowerCase();if(e.name)p+='[name=\\\"'+String(e.name).replace(/\\\"/g,'')+'\\\"]';return p};const arr=[...document.querySelectorAll('a,button,input,select,textarea,[role=button],[role=link],[tabindex]')].slice(0,160);const items=[];for(const e of arr){const st=getComputedStyle(e);if(st.display==='none'||st.visibility==='hidden')continue;const type=(e.type||'').toLowerCase(),ac=(e.autocomplete||'').toLowerCase(),meta=[e.id,e.name,e.getAttribute('aria-label'),e.placeholder].filter(Boolean).join(' ');if(type==='password'||ac.includes('password')||ac.includes('one-time-code')||rx.test(meta))continue;items.push({tag:e.tagName.toLowerCase(),role:e.getAttribute('role')||'',text:(e.innerText||e.getAttribute('aria-label')||'').trim().slice(0,200),aria:e.getAttribute('aria-label')||'',placeholder:e.placeholder||'',type:type,id:e.id||'',name:e.name||'',selector:sel(e)});if(items.length>=120)break;}return JSON.stringify({ok:true,url:location.href,title:document.title,elements:items});}catch(x){return JSON.stringify({ok:false,error:'snapshot_failed'});}})();";
    }

    private static String result(boolean ok, String error) {
        return "(function(){return JSON.stringify({ok:" + ok + ",error:" + q(error) + "});})();";
    }
}
