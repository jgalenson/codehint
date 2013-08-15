package codehint.exprgen;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

public class UntypedExpression {
	
	protected final Expression expression;

	public UntypedExpression(Expression expression) {
		this.expression = expression;
	}
	
	public Expression getExpression() {
		return expression;
	}
	
	public IJavaType getType() {
		return null;
	}
	
	// TODO: I should get rid of get{Value,Result} now that I have effects.  You should only be able to get a value or result given the current effects to be sure you don't accidentally forget to use it.
	
	public IJavaValue getValue() {
		return null;
	}
	
	public Value getWrapperValue() {
		return null;
	}
	
	public Result getResult() {
		return null;
	}

	@Override
	public String toString() {
		if (expression == null)
			return "";
		else
			return expression.toString();
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
		if (!(obj instanceof UntypedExpression))
			return false;
		UntypedExpression other = (UntypedExpression) obj;
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

}