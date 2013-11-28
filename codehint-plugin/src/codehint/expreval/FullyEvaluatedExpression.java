package codehint.expreval;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.ast.Expression;
import codehint.exprgen.Result;

/**
 * A class that stores an expression, its type,
 * its value, and its toString().
 */
public class FullyEvaluatedExpression extends EvaluatedExpression {
	
	private final String resultString;
	
	public FullyEvaluatedExpression(Expression expression, IJavaType type, Result result, String resultString) {
		super(expression, type, result);
		this.resultString = resultString;
	}
	
	public String getResultString() {
		return resultString;
	}
	
	/**
	 * Gets a list of the snippets of the given evaluated expressions.
	 * @param results List of evaluated expressions.
	 * @return The snippets of the given evaluated expressions.
	 */
	public static List<String> snippetsOfEvaluatedExpressions(List<FullyEvaluatedExpression> results) {
		List<String> resultStrs = new ArrayList<String>(results.size());
		for (FullyEvaluatedExpression result : results)
			resultStrs.add(result.getSnippet());
		return resultStrs;
	}
	
}
