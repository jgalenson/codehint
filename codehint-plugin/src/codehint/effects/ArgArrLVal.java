package codehint.effects;


import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;

public class ArgArrLVal extends LVal {
	
	private final ArrayReference arr;
	
	public ArgArrLVal(ArrayReference arr) {
		this.arr = arr;
	}

	@Override
	public Value getValue() {
		return arr;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((arr == null) ? 0 : arr.hashCode());
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
		ArgArrLVal other = (ArgArrLVal) obj;
		if (arr == null) {
			if (other.arr != null)
				return false;
		} else if (!arr.equals(other.arr))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return arr.toString();
	}

	@Override
	public void setValue(Value value) throws InvalidTypeException, ClassNotLoadedException {
		((ArrayValue)value).resetTo(arr);
	}
	
}