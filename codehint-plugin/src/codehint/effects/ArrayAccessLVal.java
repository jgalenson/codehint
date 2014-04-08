package codehint.effects;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;

public class ArrayAccessLVal extends ArgArrLVal {
	
	private final int index;

	public ArrayAccessLVal(ArrayReference arr, int index) {
		super(arr);
		this.index = index;
	}

	@Override
	public Value getValue() {
		return arr.getValue(index);
	}

	@Override
	public void setValue(Value value) throws InvalidTypeException, ClassNotLoadedException {
		arr.setValue(index, value);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + index;
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
		ArrayAccessLVal other = (ArrayAccessLVal) obj;
		if (index != other.index)
			return false;
		if (arr == null) {
			if (other.arr != null)
				return false;
		} else if (!arr.equals(other.arr))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return super.toString() + "[" + index + "]";
	}

}
