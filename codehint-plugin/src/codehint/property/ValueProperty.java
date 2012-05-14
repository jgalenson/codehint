package codehint.property;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaValue;

public abstract class ValueProperty extends LambdaProperty {
	
	protected final IJavaValue value;

	protected ValueProperty(String lhs, Expression rhs, IJavaValue value) {
		super(lhs, null, rhs);
		this.value = value;
	}
	
	public IJavaValue getValue() {
		return value;
	}

}
