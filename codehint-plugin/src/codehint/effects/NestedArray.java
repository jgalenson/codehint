package codehint.effects;

import java.util.Arrays;
import java.util.List;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;

public class NestedArray extends ArrayValue {
	
	private final ArrayValue[] nestedValues;
	
	public NestedArray(ArrayReference value, List<Value> values, ArrayValue[] nestedValues) {
		super(value, values);
		this.nestedValues = nestedValues;
	}
	
	@Override
	public boolean equals(ArrayReference newValue) {
		if (!newValue.getValues().equals(values))
			return false;
		List<Value> newValues = SideEffectHandler.getValues(newValue);
		for (int i = 0; i < nestedValues.length; i++) {
			Value nestedValue = newValues.get(i);
			if (nestedValue != null)
				if (!nestedValues[i].equals((ArrayReference)nestedValue))
					return false;
		}
		return true;
	}
	
	@Override
	public void resetTo(ArrayReference newValue) throws InvalidTypeException, ClassNotLoadedException {
		newValue.setValues(values);
		List<Value> newValues = SideEffectHandler.getValues(newValue);
		for (int i = 0; i < nestedValues.length; i++) {
			Value nestedValue = newValues.get(i);
			if (nestedValue != null)
				nestedValues[i].resetTo((ArrayReference)nestedValue);
		}
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Arrays.hashCode(nestedValues);
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
		NestedArray other = (NestedArray) obj;
		if (!Arrays.equals(nestedValues, other.nestedValues))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (ArrayValue nestedValue: nestedValues) {
			if (sb.length() > 1)
				sb.append(",");
			sb.append(nestedValue.toString());
		}
		sb.append("]");
		return sb.toString();
	}
	
}