package codehint.effects;

import java.util.List;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;

public class SimpleArray extends ArrayValue {
	
	SimpleArray(ArrayReference value, List<Value> values) {
		super(value, values);
	}
	
	@Override
	public boolean equals(ArrayReference newValue) {
		if (newValue == null)
			return values == null;
		return SideEffectHandler.getValues(newValue).equals(values);
	}
	
	@Override
	public void resetTo(ArrayReference newValue) throws InvalidTypeException, ClassNotLoadedException {
		newValue.setValues(values);
	}
	
	@Override
	public String toString() {
		return String.valueOf(values);
	}
	
}