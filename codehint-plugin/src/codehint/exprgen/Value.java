package codehint.exprgen;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * A class that wraps IJavaValue and implements a stronger
 * equality check.
 * Two different Strings with the same value will not
 * be equal w.r.t. IJavaValue but will w.r.t. Value. 
 */
public class Value {
	
	private final IJavaValue value;
	private final String valueToString;

	public Value(IJavaValue value) {
		this.value = value;
		try {
			valueToString = value == null ? "null" : value.getSignature() + "~" + value.toString();  // Combine the value's type and its toString; the former is needed because Strings and primitives give their value as ToString while Objects give their type and id. 
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	public IJavaValue getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((valueToString == null) ? 0 : valueToString.hashCode());
		return result;
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
		if (valueToString == null) {
			if (other.valueToString != null)
				return false;
		} else if (!valueToString.equals(other.valueToString))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return valueToString;
	}

}
