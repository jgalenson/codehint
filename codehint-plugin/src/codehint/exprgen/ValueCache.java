package codehint.exprgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * A class that caches Value wrappers.
 */
public class ValueCache {
	
	private final IJavaDebugTarget target;
	private final Map<IJavaValue, Value> valueCache;
	private final Map<Integer, IJavaValue> intVals;
	private IJavaValue f;
	private IJavaValue t;
	private final Map<String, IJavaValue> stringVals;
	private final ArrayList<IJavaObject> collectionDisableds;
	
	public ValueCache(IJavaDebugTarget target) {
		this.target = target;
		valueCache = new HashMap<IJavaValue, Value>();
		intVals = new HashMap<Integer, IJavaValue>();
		t = null;
		f = null;
		stringVals = new HashMap<String, IJavaValue>();
		collectionDisableds = new ArrayList<IJavaObject>();
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
	
	/**
	 * Returns the IJavaValue for the given int.
	 * @param n An int.
	 * @return The IJavaValue for the given int.
	 */
	public IJavaValue getIntJavaValue(int n) {
		IJavaValue value = intVals.get(n);
		if (value == null) {
			value = target.newValue(n);
			intVals.put(n, value);
		}
		return value;
	}

	/**
	 * Returns the IJavaValue for the given boolean.
	 * @param b A boolean.
	 * @return The IJavaValue for the given boolean.
	 */
	public IJavaValue getBooleanJavaValue(boolean b) {
		if (b) {
			if (t == null)
				t = target.newValue(b);
			return t;
		} else {
			if (f == null)
				f = target.newValue(b);
			return f;
		}
	}

	/**
	 * Returns the IJavaValue for the given String.
	 * @param s a String.
	 * @return The IJavaValue for the given tring.
	 */
	public IJavaValue getStringJavaValue(String s) {
		IJavaValue value = stringVals.get(s);
		if (value == null) {
			value = disableObjectCollection((IJavaObject)target.newValue(s));
			stringVals.put(s, value);
		}
		return value;
	}
	
	/**
	 * Allow the strings we created ourselves to be collected.
	 */
	public void allowCollectionOfNewStrings() {
		try {
			for (IJavaObject obj: collectionDisableds)
				obj.enableCollection();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		collectionDisableds.clear();
	}

	// We must disable collection on these strings and arrays since they are not reachable and hence could be collected.  Without this, the strings are collected and the EvaluationManager inserts non-quoted string literals for them and crashes.
	public <T extends IJavaObject> T disableObjectCollection(T o) {
		try {
			o.disableCollection();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		collectionDisableds.add(o);
		return o;
	}

}
