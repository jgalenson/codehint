package codehint.exprgen;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * Wrapper class that stores an expression and its type.
 */
public class TypedExpression {
	
	private final Expression expression;
	private final IJavaType type;
	private final IJavaValue value;
	
	public TypedExpression(Expression expression, IJavaType type, IJavaValue value) {
		this.expression = expression;
		this.type = type;
		this.value = value;
	}
	
	public Expression getExpression() {
		return expression;
	}
	
	public IJavaType getType() {
		return type;
	}
	
	public IJavaValue getValue() {
		return value;
	}
	
	@Override
	public String toString() {
		if (expression == null)
			return "";
		else if (value == null)
			return expression.toString();
		else
			return expression.toString() + " (= " + value.toString() + ")";
	}
	
}
