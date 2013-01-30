package codehint.effects;


import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;

public abstract class FieldLVal extends LVal {
	
	protected final Field field;
	
	public FieldLVal(Field field) {
		this.field = field;
	}
	
	public static FieldLVal makeFieldLVal(ObjectReference obj, Field field) {
		if (obj == null)
			return new StaticLVal(field);
		else
			return new ObjLVal(obj, field);
	}
	
	public abstract ObjectReference getObject();
	
	public Field getField() {
		return field;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((field == null) ? 0 : field.hashCode());
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
		FieldLVal other = (FieldLVal) obj;
		if (field == null) {
			if (other.field != null)
				return false;
		} else if (!field.equals(other.field))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "." + field.name();
	}
	
}