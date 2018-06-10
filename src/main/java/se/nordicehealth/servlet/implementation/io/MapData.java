package se.nordicehealth.servlet.implementation.io;

import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONObject;

public class MapData
{
	
	@Override
	public String toString() { return o.toString(); }
	public void put(Enum<?> k, Enum<?> v) { m.put(etos(k), etos(v)); }
	public void put(Enum<?> k, String v) { m.put(etos(k), v); }
	public void put(Enum<?> k, Integer v) { m.put(etos(k), itos(v)); }
	public void put(Integer k, String v) { m.put(itos(k), v); }
	public void put(String k, Integer v) { m.put(k, itos(v)); }
	public String get(Enum<?> k) { return m.get(etos(k)); }

    public Iterable<Entry<String, String>> iterable() {
        return m.entrySet();
    }
	
	public MapData()
	{
		this((JSONObject)null);
	}
	
	@SuppressWarnings("unchecked")
	public MapData(JSONObject o)
	{
		this.o = o != null ? o : new JSONObject();
		this.m = (Map<String, String>) this.o;
	}
	
	private JSONObject o;
	private Map<String, String> m;
	
	private String itos(int i) { return Integer.toString(i); }
	private String etos(Enum<?> e) { return Integer.toString(e.ordinal()); }
}