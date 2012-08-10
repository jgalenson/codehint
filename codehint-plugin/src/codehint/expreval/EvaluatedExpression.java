package codehint.expreval;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.exprgen.TypedExpression;
import codehint.exprgen.Value;

/**
 * A class that stores an expression, its type,
 * and its value.
 */
public class EvaluatedExpression extends TypedExpression {
	
	protected final Value value;
	
	public EvaluatedExpression(Expression expression, IJavaType type, Value value) {
		super(expression, type);
		assert value != null;
		this.value = value;
	}
	
	public String getSnippet() {
		return expression.toString();
	}
	
	@Override
	public IJavaValue getValue() {
		return value.getValue();
	}
	
	@Override
	public Value getWrapperValue() {
		return value;
	}
	
	public static TypedExpression makeTypedOrEvaluatedExpression(Expression expression, IJavaType type, Value value) {
		if (value == null)
			return new TypedExpression(expression, type);
		else
			return new EvaluatedExpression(expression, type, value);
	}

	@Override
	public String toString() {
		if (value == null)
			return getSnippet();
		else
			return getSnippet() + " (= " + value.toString() + ")";
	}
	
}
