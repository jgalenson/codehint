package codehint.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		ArrayList<V> list = map.get(key);
		if (list == null) {
			list = new ArrayList<V>();
			map.put(key,  list);
		}
		list.add(value);
	}
	
	/**
	 * Adds the given key-value mapping to the given map.
	 * @param map The map.
	 * @param key1 The outer key.
	 * @param key2 The inner key.
	 * @param value The value.
	 */
	public static <K1, K2, V> void addToMapMap(Map<K1, Map<K2, V>> map, K1 key1, K2 key2, V value) {
		Map<K2, V> innerMap = map.get(key1);
		if (innerMap == null) {
			innerMap = new HashMap<K2, V>();
			map.put(key1,  innerMap);
		}
		innerMap.put(key2, value);
	}
	
	/**
	 * Adds the given key-value mapping to the given map.
	 * @param map The map.
	 * @param key The key.
	 * @param value The value.
	 */
	public static <K, V> void addToSetMap(Map<K, Set<V>> map, K key, V value) {
		Set<V> set = map.get(key);
		if (set == null) {
			set = new HashSet<V>();
			map.put(key,  set);
		}
		set.add(value);
	}
	
	/**
	 * Adds the given key-value mapping to the given map.
	 * @param map The map.
	 * @param key The key.
	 * @param values The values.
	 */
	public static <K, V> void addAllToListMap(Map<K, ArrayList<V>> map, K key, ArrayList<V> values) {
		ArrayList<V> list = map.get(key);
		if (list == null) {
			list = new ArrayList<V>();
			map.put(key,  list);
		}
		list.addAll(values);
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
	 * Gets the number of calls that will be created from
	 * the given list of possible actuals.
	 * @param allPossibleActuals A list of all the possible
	 * actuals for each argument.
	 * @return The number of calls with the given possible actuals.
	 */
	public static long getNumCalls(Collection<? extends ArrayList<?>> allPossibleActuals) {
		long total = 1L;
		for (ArrayList<?> possibleActuals: allPossibleActuals) {
			total *= possibleActuals.size();
			if (total < 0)  // Detect overflows.
				return Long.MAX_VALUE;
		}
		return total;
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
	
	/**
	 * Increments the integer stored for the given
	 * key in the given map.
	 * @param map The map.
	 * @param key The key.
	 * @returns The new integer.
	 */
	public static <T> int incrementMap(Map<T, Integer> map, T key) {
		Integer num = map.get(key);
		int newNum = num == null ? 1 : num + 1;
		map.put(key, newNum);
		return newNum;
	}
	
	/**
	 * Gets the sum of the values in the given map.
	 * @param map The map.
	 * @return The sum of the integer values in the given map.
	 */
	public static int getValueTotal(Map<?, Integer> map) {
		int count = 0;
		for (Integer n: map.values())
			count += n;
		return count;
	}
	
	/**
	 * Gets a printable version of the given String
	 * by quoting unprintable characters like newlines.
	 * @param s The string.
	 * @return A printable version of the given String.
	 */
	public static String getPrintableString(String s) {
		return s.replace("\n", "\\n").replace("\r", "\\r");
	}
	
    public static String arrayToString(Object[] a) {
        if (a == null)
            return "null";
        int iMax = a.length - 1;
        if (iMax == -1)
            return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; ; i++) {
            b.append(String.valueOf(a[i]));
            if (i == iMax)
                return b.toString();
            b.append(", ");
        }
    }
	
	public static String getEscapedValue(char ch) {
		if (ch == '\b')
			return "\\b";
		if (ch == '\t')
			return "\\t";
		if (ch == '\n')
			return "\\n";
		if (ch == '\f')
			return "\\f";
		if (ch == '\r')
			return "\\r";
		if (ch == '"')
			return "\\\"";
		if (ch == '\'')
			return "\\'";
		if (ch == '\\')
			return "\\";
		return String.valueOf(ch);
	}

}
