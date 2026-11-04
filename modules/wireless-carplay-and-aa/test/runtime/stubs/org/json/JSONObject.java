package org.json;
public class JSONObject {
    private final java.util.Map<String,Object> map = new java.util.LinkedHashMap<String,Object>();
    public JSONObject put(String k, Object v) { map.put(k,v); return this; }
    public JSONObject getJSONObject(String k) { return (JSONObject)map.get(k); }
    public java.util.Iterator<String> keys() { return map.keySet().iterator(); }
    public Object get(String k) { return map.get(k); }
}
