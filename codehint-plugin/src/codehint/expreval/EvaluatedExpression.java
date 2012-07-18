package codehint.expreval;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

/**
 * The result of an evaluation, which contains the snippet of
 * code that was evaluated and the result.
 */
public class EvaluatedExpression {
	
	private final Expression expr;
	private final IJavaValue result;
	private final IJavaType type;
	private final String resultString;
	
	public EvaluatedExpression(Expression expr, IJavaValue result, IJavaType type, String resultString) {
		this.expr = expr;
		this.result = result;
		this.type = type;
		this.resultString = resultString;
	}
	
	public Expression getExpression() {
		return expr;
	}
	
	public String getSnippet() {
		return expr.toString();
	}
	
	public IJavaValue getResult() {
		return result;
	}
	
	public IJavaType getType() {
		return type;
	}
	
	public String getResultString() {
		return resultString;
	}
	
	/**
	 * Gets a list of the snippets of the given evaluated expressions.
	 * @param results List of evaluated expressions.
	 * @return The snippets of the given evaluated expressions.
	 */
	public static List<String> snippetsOfEvaluatedExpressions(List<EvaluatedExpression> results) {
		List<String> resultStrs = new ArrayList<String>(results.size());
		for (EvaluatedExpression result : results)
			resultStrs.add(result.getSnippet());
		return resultStrs;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((expr == null) ? 0 : expr.toString().hashCode());  // Hash the toString not the expression (which uses the address)....
		result = prime * result + ((this.result == null) ? 0 : this.result.hashCode());
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
		EvaluatedExpression other = (EvaluatedExpression) obj;
		if (expr == null) {
			if (other.expr != null)
				return false;
		} else {
			ASTMatcher astMatcher = new ASTMatcher();
			if (!expr.subtreeMatch(astMatcher, other.expr))  // ASTNode.equals uses reference equality....
				return false;
		}
		if (result == null) {
			if (other.result != null)
				return false;
		} else if (!result.equals(other.result))
			return false;
		return true;
	}

	@Override
	public String toString() {
		if (result == null)
			return getSnippet();
		else
			return getSnippet() + " (= " + result.toString() + ")";
	}
	
}
