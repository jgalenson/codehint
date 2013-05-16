package codehint.exprgen;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaClassObject;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * A class that wraps IJavaValue and implements a stronger
 * equality check.
 * Two different Strings with the same value will not
 * be equal w.r.t. IJavaValue but will w.r.t. Value. 
 */
public class Value {
	
	protected final IJavaValue value;
	private final int hashCode;
	private final IJavaThread thread;
	private final ValueCache valueCache;

	protected Value(IJavaValue value, IJavaThread thread, ValueCache valueCache) {
		this.value = value;
		this.thread = thread;
		this.hashCode = getMyHashCode();
		this.valueCache = valueCache;
	}
	
	public static Value makeValue(IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		try {
			Value wrapper = valueCache.getValue(value);
			if (wrapper != null)
				return wrapper;
			if (value != null && "Ljava/lang/String;".equals(value.getSignature()))
				wrapper = new StringValue(value, thread, valueCache);
			else
				wrapper = new Value(value, thread, valueCache);
			valueCache.addValue(wrapper);
			return wrapper;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected int getMyHashCode() {
		try {
			return getHashCode(value);
		} catch (DebugException e) {
			return Integer.MIN_VALUE + 42;
		}
	}

	protected int getHashCode(IJavaValue value) throws DebugException {
		if (value == null || value.isNull())
			return -37;
		else if (value instanceof IJavaPrimitiveValue)
			return ((IJavaPrimitiveValue)value).getIntValue() * 5 + 137;  // We add a number to avoid clustering around 0. 
		else if (value instanceof IJavaArray) {  // Heuristically only look at the array's length and its first ten elements.
			IJavaArray array = (IJavaArray)value;
			int length = array.getLength();
			int hashCode = length * 5 + array.getSignature().hashCode() * 7;
			for (int i = 0; i < 10 && i < length; i++)
				hashCode = 31 * hashCode + getHashCode(array.getValue(i));
			return hashCode;
		} else if (value instanceof IJavaClassObject)
			return ((IJavaClassObject)value).getInstanceType().getName().hashCode() * 11;
		else if ("V".equals(value.getSignature()))
			return -137;
		else {
			IJavaObject obj = (IJavaObject)value;
			if ("Ljava/lang/String;".equals(obj.getSignature()))  // Fast-path Strings
				return obj.toString().hashCode();
			return ((IJavaPrimitiveValue)obj.sendMessage("hashCode", "()I", new IJavaValue[] { }, thread, null)).getIntValue();
		}
	}
	
	public IJavaValue getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return equals((Value)obj);
	}
	
	protected boolean equals(Value other) {
		return equals(value, other.value, thread, valueCache);
	}
	
	private static boolean equals(IJavaValue x, IJavaValue y, IJavaThread thread, ValueCache valueCache) {
		try {
			if (x == y || (x.isNull() && y.isNull()))
				return true;
			if (x.isNull() || y.isNull())
				return false;
			if (x instanceof IJavaArray && y instanceof IJavaArray) {
				if (!x.getSignature().equals(y.getSignature()))
					return false;  // Even though arrays are covariant, using the subtype could crash when the supertype does not (e.g., normal Java array covariange bug).
				IJavaArray a = (IJavaArray)x;
				IJavaArray b = (IJavaArray)y;
				if (a.getLength() != b.getLength())
					return false;
				for (int i = 0; i < a.getLength(); i++)  // TODO-opt: Should I finitize this so it doesn't take too long?
					if (!equals(a.getValue(i), b.getValue(i), thread, valueCache))  // Recurse on the actual values.
						return false;
				return true;
			}
			String signature = x.getSignature();
			if (!signature.equals(y.getSignature()))  // Short circuit if the two values are not the same type.
				return false;
			if ("V".equals(signature))
				return true;
			if (x instanceof IJavaPrimitiveValue || "Ljava/lang/String;".equals(signature))
				return x.toString().equals(y.toString());
			else
				return valueCache.checkObjectEquality((IJavaObject)x, (IJavaObject)y, thread);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected static boolean objectEquals(IJavaObject o1, IJavaObject o2, IJavaThread thread) throws DebugException {
		if (o1.getUniqueId() == o2.getUniqueId())  // Short circuit check for == equality.
			return true;
		System.out.println(o1 + " .equals " + o2);
		return ((IJavaPrimitiveValue)o1.sendMessage("equals", "(Ljava/lang/Object;)Z", new IJavaValue[] { o2 }, thread, null)).getBooleanValue();
	}

	@Override
	public String toString() {
		return getToString(value);
	}
	
	/**
	 * Gets a readable toString by looking inside arrays.
	 * @param value The value whose toString we want.
	 * @return A toString that looks inside arrays.
	 */
	private static String getToString(IJavaValue value) {
		try {
			if (value instanceof IJavaArray) {
				IJavaArray arr = (IJavaArray)value;
	    		StringBuilder sb = new StringBuilder();
	    		sb.append("[");
	    		for (int i = 0; i < 5 && i < arr.getLength(); i++) {
	    			if (i > 0)
	    				sb.append(",");
	    			sb.append(getToString(arr.getValue(i)));
	    		}
	    		if (arr.getLength() > 5)
	    			sb.append(",").append(arr.getLength() - 5).append(" more...");
	    		sb.append("]");
	    		return sb.toString();
	    	} else
	    		return value.toString();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
