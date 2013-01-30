package codehint.effects;

import java.util.List;


import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;

public abstract class ArrayValue extends RVal {
	
	protected final List<Value> values;

	protected ArrayValue(ArrayReference value, List<Value> values) {
		super(value);
		this.values = values;
	}
	
	public static ArrayValue makeArrayValue(ArrayReference value) {
		if (value == null)
			return new SimpleArray(value, null);
		String typeName = value.type().name();
		if (typeName.indexOf(']') == typeName.length() - 1)
			return new SimpleArray(value, SideEffectHandler.getValues(value));
		else {
			List<Value> values = SideEffectHandler.getValues(value);
			ArrayValue[] nestedCopies = new ArrayValue[values.size()];
			for (int i = 0; i < nestedCopies.length; i++)
				nestedCopies[i] = makeArrayValue((ArrayReference)values.get(i));
			return new NestedArray(value, values, nestedCopies);
		}
	}

	public abstract boolean equals(ArrayReference newValue);

	public abstract void resetTo(ArrayReference newValue) throws InvalidTypeException, ClassNotLoadedException;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((values == null) ? 0 : values.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ArrayValue other = (ArrayValue) obj;
		if (values == null) {
			if (other.values != null)
				return false;
		} else if (!values.equals(other.values))
			return false;
		return true;
	}
	
}