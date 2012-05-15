package codehint.property;

import org.eclipse.jdt.debug.core.IJavaStackFrame;

public abstract class Property {

	public abstract String getReplacedString(String arg, IJavaStackFrame stack);
	
}
