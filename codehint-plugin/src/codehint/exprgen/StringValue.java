package codehint.exprgen;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * A class for StringValues.
 * Getting the value of a String involves
 * calling into the VM and is not cached,
 * so we cache it for efficiency.
 */
public class StringValue extends Value {
	
	private String stringValue;

	public StringValue(IJavaValue value, IJavaThread thread) {
		super(value, thread);
		try {
			stringValue = value.getValueString();  // toString returns it with the quotes, getValueString returns it without them.
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int hashCode() {
		return stringValue.hashCode();
	}
	
	@Override
	protected boolean equals(Value other) {
		if (other instanceof StringValue)
			return stringValue.equals(((StringValue)other).stringValue);
		else
			return false;
	}
	
	public String getStringValue() {
		return stringValue;
	}

	@Override
	public String toString() {
		return "\"" + stringValue + "\"";
	}

}
