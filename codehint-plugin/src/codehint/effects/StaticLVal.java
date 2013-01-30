package codehint.effects;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class StaticLVal extends FieldLVal {
	
	public StaticLVal(Field field) {
		super(field);
	}

	@Override
	public Value getValue() {
		return ((ClassType)field.declaringType()).getValue(field);
	}

	@Override
	public void setValue(Value value) throws InvalidTypeException, ClassNotLoadedException {
		((ClassType)field.declaringType()).setValue(field, value);
	}

	@Override
	public ObjectReference getObject() {
		return null;
	}
	
	@Override
	public String toString() {
		return field.declaringType().toString() + super.toString();
	}
	
}