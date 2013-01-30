package codehint.effects;


import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;

public abstract class RVal {
	
	protected final Value value;
	
	protected RVal(Value value) {
		this.value = value;
	}
	
	public static RVal makeRVal(Value value) {
		if (value instanceof ArrayReference)
			return ArrayValue.makeArrayValue(((ArrayReference)value));
		else
			return new SimpleRVal(value);
	}
	
	public Value getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		RVal other = (RVal) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}
	
}