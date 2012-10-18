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
	
	private final IJavaValue value;
	private final int hashCode;
	private final IJavaThread thread;

	public Value(IJavaValue value, IJavaThread thread) {
		this.value = value;
		this.thread = thread;
		try {
			this.hashCode = getHashCode(value);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	private int getHashCode(IJavaValue value) throws DebugException {
		if (value == null || value.isNull())
			return -37;
		else if (value instanceof IJavaPrimitiveValue)
			return ((IJavaPrimitiveValue)value).getIntValue() * 5 + 137;  // We add a number to avoid clustering around 0. 
		else if (value instanceof IJavaArray) {  // Heuristically only look at the array's length and its first ten elements.
			IJavaArray array = (IJavaArray)value;
			int length = array.getLength();
			int hashCode = length * 5;
			for (int i = 0; i < 10 && i < length; i++)
				hashCode += 7 * getHashCode(array.getValue(i));
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
		Value other = (Value) obj;
		return equals(value, other.value, thread);
	}
	
	private static boolean equals(IJavaValue x, IJavaValue y, IJavaThread thread) {
		try {
			if (x == y || (x.isNull() && y.isNull()))
				return true;
			if (x == null || y == null || x.isNull() || y.isNull())
				return false;
			String signature = x.getSignature();
			if (!signature.equals(y.getSignature()))  // Short circuit if the two values are not the same type.
				return false;
			if ("V".equals(signature))
				return true;
			if (x instanceof IJavaPrimitiveValue || "Ljava/lang/String;".equals(signature))
				return x.toString().equals(y.toString());
			else if (x instanceof IJavaArray) {
				IJavaArray a = (IJavaArray)x;
				IJavaArray b = (IJavaArray)y;
				if (a.getLength() != b.getLength())
					return false;
				for (int i = 0; i < a.getLength(); i++)  // TODO-opt: Should I finitize this so it doesn't take too long?
					if (!equals(a.getValue(i), b.getValue(i), thread))  // Recurse on the actual values.
						return false;
				return true;
			} else
				return ((IJavaPrimitiveValue)((IJavaObject)x).sendMessage("equals", "(Ljava/lang/Object;)Z", new IJavaValue[] { y }, thread, null)).getBooleanValue();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
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
