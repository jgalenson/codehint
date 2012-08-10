package codehint.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class Utils {
	
	public static <T> T singleton(Collection<T> xs) {
		assert xs.size() == 1;
		return xs.iterator().next();
	}
	
	public static <K, V> void addToMap(Map<K, ArrayList<V>> map, K key, V value) {
		if (!map.containsKey(key))
			map.put(key, new ArrayList<V>());
		map.get(key).add(value);
	}
	
	public static <K, V> void addAllToMap(Map<K, ArrayList<V>> map, K key, ArrayList<V> values) {
		if (!map.containsKey(key))
			map.put(key, new ArrayList<V>());
		map.get(key).addAll(values);
	}
	
	public static String plural(String str, String suffix, int count) {
		if (count == 1)
			return str;
		else
			return str + suffix;
	}
	
	public static String truncate(String str, int length) {
		if (str.length() <= length)
			return str;
		else
			return str.substring(0, length) + "...";
	}
	
	public static <T> ArrayList<T> makeList(T... ts) {
		ArrayList<T> list = new ArrayList<T>(ts.length);
		for (T t: ts)
			list.add(t);
		return list;
	}

}
