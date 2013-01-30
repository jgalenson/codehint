package codehint.effects;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class ObjLVal extends FieldLVal {
	
	private final ObjectReference obj;
	
	public ObjLVal(ObjectReference obj, Field field) {
		super(field);
		this.obj = obj;
	}

	@Override
	public Value getValue() {
		return obj.getValue(field);
	}

	@Override
	public void setValue(Value value) throws InvalidTypeException, ClassNotLoadedException {
		obj.setValue(field, value);
	}

	@Override
	public ObjectReference getObject() {
		return obj;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((obj == null) ? 0 : obj.hashCode());
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
		ObjLVal other = (ObjLVal) obj;
		if (this.obj == null) {
			if (other.obj != null)
				return false;
		} else if (!this.obj.equals(other.obj))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return obj.toString() + super.toString();
	}
	
}