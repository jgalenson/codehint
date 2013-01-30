package codehint.effects;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Value;

public abstract class LVal {
	
	public abstract Value getValue();
	
	public abstract void setValue(Value value) throws InvalidTypeException, ClassNotLoadedException;
	
}