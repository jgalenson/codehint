package codehint.effects;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;

public class Effect {
	
	private final LVal lval;
	private final RVal oldVal;
	private final RVal newVal;
	
	public Effect(LVal lval, RVal oldVal, RVal newVal) {
		this.lval = lval;
		this.oldVal = oldVal;
		this.newVal = newVal;
	}

	public LVal getLval() {
		return lval;
	}

	public RVal getOldVal() {
		return oldVal;
	}

	public RVal getNewVal() {
		return newVal;
	}
	
	public void undo() throws InvalidTypeException, ClassNotLoadedException {
		doEffect(newVal, oldVal);
	}
	
	public void redo() throws InvalidTypeException, ClassNotLoadedException {
		doEffect(oldVal, newVal);
	}
	
	public void doEffect(RVal preVal, RVal postVal) throws InvalidTypeException, ClassNotLoadedException {
		if (postVal instanceof ArrayValue) {
			ArrayValue postArrVal = ((ArrayValue)postVal);
			if (preVal.getValue() instanceof ArrayReference) {
				ArrayReference preArrVal = (ArrayReference)preVal.getValue();
				if (postArrVal.getValue() instanceof ArrayReference && ((ArrayReference)postArrVal.getValue()).uniqueID() == preArrVal.uniqueID()) {
					// If it's the same array just with different values, we can reset the values directly.
					postArrVal.resetTo(preArrVal);
					return;
				}
			}
			// We must reset the original array's values and reset to it.
			postArrVal.resetTo((ArrayReference)postArrVal.getValue());
		}
		lval.setValue(postVal.getValue());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lval == null) ? 0 : lval.hashCode());
		result = prime * result + ((newVal == null) ? 0 : newVal.hashCode());
		result = prime * result + ((oldVal == null) ? 0 : oldVal.hashCode());
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
		Effect other = (Effect) obj;
		if (lval == null) {
			if (other.lval != null)
				return false;
		} else if (!lval.equals(other.lval))
			return false;
		if (newVal == null) {
			if (other.newVal != null)
				return false;
		} else if (!newVal.equals(other.newVal))
			return false;
		if (oldVal == null) {
			if (other.oldVal != null)
				return false;
		} else if (!oldVal.equals(other.oldVal))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return lval + " changed from " + oldVal + " to " + newVal;
	}

	
	
}
