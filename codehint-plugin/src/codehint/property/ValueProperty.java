package codehint.property;

import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.ast.Expression;

public abstract class ValueProperty extends LambdaProperty {
	
	protected final String valueString;
	protected final IJavaValue value;

	protected ValueProperty(String lhs, Expression rhs, String valueString, IJavaValue value) {
		super(lhs, null, rhs);
		this.valueString = valueString;
		this.value = value;
	}
	
	public String getValueString() {
		return valueString;
	}
	
	public IJavaValue getValue() {
		return value;
	}

}
