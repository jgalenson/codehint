package codehint.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class Utils {
	
	public static <T> T singleton(Collection<T> xs) {
		assert xs.size() == 1;
		return xs.iterator().next();
	}
	
	public static <T> void addToMap(Map<String, ArrayList<T>> map, String key, T value) {
		if (!map.containsKey(key))
			map.put(key,  new ArrayList<T>());
		map.get(key).add(value);
	}
	
	public static <T> void addAllToMap(Map<String, ArrayList<T>> map, String key, ArrayList<T> values) {
		if (!map.containsKey(key))
			map.put(key,  new ArrayList<T>());
		map.get(key).addAll(values);
	}
	
	public static String plural(String str, String suffix, int count) {
		if (count == 1)
			return str;
		else
			return str + suffix;
	}

}
