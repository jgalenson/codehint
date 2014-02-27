package codehint.exprgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.utils.UnorderedPair;

/**
 * A class that caches Value wrappers.
 */
public class ValueCache {
	
	private final IJavaDebugTarget target;
	private final IJavaThread thread;
	private final Map<IJavaValue, Value> valueCache;
	private final Map<Integer, Value> intVals;
	private final Value f;
	private final Value t;
	private final Map<String, Value> stringVals;
	private final ArrayList<IJavaObject> collectionDisableds;
	private final Map<UnorderedPair<IJavaObject, IJavaObject>, Boolean> objectEquals;
	private final Value voidValue;
	
	public ValueCache(IJavaDebugTarget target, IJavaThread thread) {
		this.target = target;
		this.thread = thread;
		valueCache = new HashMap<IJavaValue, Value>();
		intVals = new HashMap<Integer, Value>();
		t = Value.makeValue(target.newValue(true), this, thread);
		f = Value.makeValue(target.newValue(false), this, thread);
		stringVals = new HashMap<String, Value>();
		collectionDisableds = new ArrayList<IJavaObject>();
		objectEquals = new HashMap<UnorderedPair<IJavaObject, IJavaObject>, Boolean>();
		this.voidValue = Value.makeValue(target.voidValue(), this, thread);
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
	public Value getIntJavaValue(int n) {
		Value value = intVals.get(n);
		if (value == null) {
			value = Value.makeValue(target.newValue(n), this, thread);
			intVals.put(n, value);
		}
		return value;
	}

	/**
	 * Returns the IJavaValue for the given boolean.
	 * @param b A boolean.
	 * @return The IJavaValue for the given boolean.
	 */
	public Value getBooleanJavaValue(boolean b) {
		return b ? t : f;
	}

	/**
	 * Returns the IJavaValue for the given String.
	 * @param s a String.
	 * @return The IJavaValue for the given String.
	 */
	public Value getStringJavaValue(String s) {
		Value value = stringVals.get(s);
		try {
			if (value == null) {
				value = new StringValue(disableObjectCollection((IJavaObject)target.newValue(s)), thread, this);
				stringVals.put(s, value);
			} else
				assert value.getValue().isAllocated();  // This check is somewhat slow, so we put it in an assertion.
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		return value;
	}
	
	/**
	 * Allows the objects we created ourselves to be collected.
	 * Note that once we do this any values we have stored (e.g.,
	 * in stringVals may be collected before we access them).
	 */
	public void allowCollectionOfDisabledObjects() {
		try {
			for (IJavaObject obj: collectionDisableds)
				obj.enableCollection();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		collectionDisableds.clear();
	}

	/**
	 * Disables collection on the given object.
	 * We must disable collection on strings and arrays since they
	 * are not reachable and hence could be collected.  Without this,
	 * the strings are collected and the EvaluationManager inserts
	 * non-quoted string literals for them and crashes.
	 * @param o The object for which we want to disable collection.
	 * @return The input object.
	 */
	public <T extends IJavaObject> T disableObjectCollection(T o) {
		try {
			o.disableCollection();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		collectionDisableds.add(o);
		return o;
	}
	
	public boolean checkObjectEquality(IJavaObject x, IJavaObject y, IJavaThread thread) throws DebugException {
		UnorderedPair<IJavaObject, IJavaObject> pair = new UnorderedPair<IJavaObject, IJavaObject>(x, y);
		Boolean resultObj = objectEquals.get(pair);
		if (resultObj != null)
			return resultObj;
		boolean result = Value.objectEquals(x, y, thread);
		objectEquals.put(pair, result);
		return result;
	}
	
	public Value voidValue() {
		return voidValue;
	}

}
