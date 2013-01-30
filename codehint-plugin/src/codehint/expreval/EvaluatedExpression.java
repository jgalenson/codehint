package codehint.expreval;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.exprgen.Result;
import codehint.exprgen.TypedExpression;
import codehint.exprgen.Value;

/**
 * A class that stores an expression, its type,
 * and its value.
 */
public class EvaluatedExpression extends TypedExpression {
	
	protected final Result result;
	
	public EvaluatedExpression(Expression expression, IJavaType type, Result result) {
		super(expression, type);
		assert result != null;
		this.result = result;
	}
	
	public String getSnippet() {
		return expression.toString();
	}
	
	@Override
	public IJavaValue getValue() {
		return result.getValue().getValue();
	}
	
	@Override
	public Value getWrapperValue() {
		return result.getValue();
	}
	
	@Override
	public Result getResult() {
		return result;
	}
	
	public static TypedExpression makeTypedOrEvaluatedExpression(Expression expression, IJavaType type, Result result) {
		if (result == null)
			return new TypedExpression(expression, type);
		else
			return new EvaluatedExpression(expression, type, result);
	}

	@Override
	public String toString() {
		if (result == null)
			return getSnippet();
		else
			return getSnippet() + " (= " + result + ")";
	}
	
}
