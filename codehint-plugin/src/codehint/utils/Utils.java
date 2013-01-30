package codehint.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
	
	/**
	 * Gets the element out of a singleton collection.
	 * Fails an assertion if the collection does
	 * not contain exactly one element.
	 * @param xs The singleton collection.
	 * @return The single element in the collection.
	 */
	public static <T> T singleton(Collection<T> xs) {
		assert xs.size() == 1;
		return xs.iterator().next();
	}
	
	/**
	 * Adds the given key-value mapping to the given map.
	 * @param map The map.
	 * @param key The key.
	 * @param value The value.
	 */
	public static <K, V> void addToListMap(Map<K, ArrayList<V>> map, K key, V value) {
		if (!map.containsKey(key))
			map.put(key, new ArrayList<V>());
		map.get(key).add(value);
	}
	
	/**
	 * Adds the given key-value mapping to the given map.
	 * @param map The map.
	 * @param key1 The outer key.
	 * @param key2 The inner key.
	 * @param value The value.
	 */
	public static <K1, K2, V> void addToMapMap(Map<K1, Map<K2, V>> map, K1 key1, K2 key2, V value) {
		if (!map.containsKey(key1))
			map.put(key1, new HashMap<K2, V>());
		map.get(key1).put(key2, value);
	}
	
	public static String plural(String str, String suffix, int count) {
		if (count == 1)
			return str;
		else
			return str + suffix;
	}
	
	/**
	 * Truncates the given string so it is no longer
	 * than the given length, which must be at least
	 * three.
	 * @param str The string to truncate.
	 * @param length The maximum length of the desired
	 * string, which must be at least three.
	 * @return The given string, truncated to at most
	 * length characters, with "..." at its end if
	 * it is truncated.
	 */
	public static String truncate(String str, int length) {
		assert length >= 3 : length;
		length = length - 3;
		if (str.length() <= length)
			return str;
		else
			return str.substring(0, length) + "...";
	}
	
	/**
	 * Converts the given array or varargs into an ArrayList.
	 * @param ts The elements to put in the list.
	 * @return An ArrayList containing the given elements.
	 */
	public static <T> ArrayList<T> makeList(T... ts) {
		ArrayList<T> list = new ArrayList<T>(ts.length);
		for (T t: ts)
			list.add(t);
		return list;
	}
	
	public static <K, V> ArrayList<V> getOrElseCreateEmpty(Map<K, ArrayList<V>> map, K key) {
		ArrayList<V> result = map.get(key);
		if (result != null)
			return result;
		else {
			result = new ArrayList<V>();
			map.put(key, result);
			return result;
		}
	}

	/**
	 * Gets the total number of values in the map.
	 * @param map A map whose values are lists of
	 * the actual values.
	 * @return The total number of values in the map.
	 */
	public static <K, V> int getNumValues(Map<K, ? extends List<V>> map) {
		int num = 0;
		for (List<V> values: map.values())
			num += values.size();
		return num;
	}

}
