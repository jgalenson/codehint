package codehint.exprgen;

import org.eclipse.jdt.core.dom.ASTMatcher;
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((expression == null) ? 0 : expression.toString().hashCode());
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
		TypedExpression other = (TypedExpression) obj;
		if (expression == null) {
			if (other.expression != null)
				return false;
		} else {
			ASTMatcher astMatcher = new ASTMatcher();
			if (!expression.subtreeMatch(astMatcher, other.expression))  // ASTNode.equals uses reference equality....
				return false;
		}
		return true;
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
