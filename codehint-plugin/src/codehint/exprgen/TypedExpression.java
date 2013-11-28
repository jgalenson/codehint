package codehint.exprgen;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.ast.Expression;

/**
 * Wrapper class that stores an expression and its type.
 */
public class TypedExpression extends UntypedExpression {
	
	protected final IJavaType type;
	
	public TypedExpression(Expression expression, IJavaType type) {
		super(expression);
		this.type = type;
		if ((type == null && expression.getStaticType() != null) || (type != null && !type.equals(expression.getStaticType())))
			throw new RuntimeException(expression.toString() + ":  " + type + "    " + expression.getStaticType());
	}
	
	@Override
	public IJavaType getType() {
		if ((type == null && expression.getStaticType() != null) || (type != null && !type.equals(expression.getStaticType())))
			throw new RuntimeException(expression.toString() + ":  " + type + "    " + expression.getStaticType());
		return expression.getStaticType();
	}
	
}
