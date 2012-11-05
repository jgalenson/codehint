package codehint.exprgen;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * A class that caches Value wrappers.
 */
public class ValueCache {
	
	private final Map<IJavaValue, Value> valueCache;
	
	public ValueCache() {
		valueCache = new HashMap<IJavaValue, Value>();
	}
	
	/**
	 * Returns the Value for the given IJavaValue,
	 * or null if none exists.
	 * @param value The value.
	 * @return The Value for the given IJavaValue,
	 * or null if none exists.
	 */
	public Value getValue(IJavaValue value) {
		return valueCache.get(value); 
	}
	
	/**
	 * Adds the value to the cache.
	 * @param value The value to add to the cache.
	 */
	public void addValue(Value value) {
		valueCache.put(value.getValue(), value);
	}

}
