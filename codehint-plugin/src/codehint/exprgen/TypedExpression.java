package codehint.exprgen;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * Wrapper class that stores an expression and its type.
 */
public class TypedExpression {
	
	protected final Expression expression;
	protected final IJavaType type;
	
	public TypedExpression(Expression expression, IJavaType type) {
		this.expression = expression;
		this.type = type;
	}
	
	public Expression getExpression() {
		return expression;
	}
	
	public IJavaType getType() {
		return type;
	}
	
	public IJavaValue getValue() {
		return null;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((expression == null) ? 0 : expression.toString().hashCode());  // Hash the toString not the expression (which uses the address)....
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		//if (getClass() != obj.getClass())
		if (!(obj instanceof TypedExpression))
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
		else
			return expression.toString();
	}
	
}
