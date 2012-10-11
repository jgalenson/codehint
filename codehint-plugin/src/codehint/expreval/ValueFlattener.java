package codehint.expreval;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.exprgen.ExpressionMaker;
import codehint.property.ASTFlattener;

/**
 * Gets the String representation of a node, substituting in
 * values for its evaluated children when possible.
 */
public class ValueFlattener extends ASTFlattener {
	
	@Override
	protected StringBuilder flatten(Expression node) {
		IJavaValue value = ExpressionMaker.getExpressionValue(node);
		if (value != null) {
			try {
				if (value instanceof IJavaPrimitiveValue) {
					String str = value.toString();
					if ("C".equals(value.getSignature()))  // Wrap characters in single quotes.
						return sb.append("'").append(str).append("'");
					else
						return sb.append(str);
				} else if (value.isNull())
					return handleCast(node, value.toString());
				else if (value instanceof IJavaObject && "Ljava/lang/String;".equals(value.getSignature())) {
					String str = value.toString();
					return handleCast(node, str.replaceAll("[\n]", "\\\\n"));  // Replace newlines.
				}
			} catch (DebugException ex) {
				throw new RuntimeException(ex);
			}
		}
		return super.flatten(node);
	}
	
	/**
	 * Append the given string to the StringBuilder,
	 * with a cast if the given node is a cast expression.
	 * This is needed because we insert casts to disambiguate
	 * overloaded methods, so without them, we will generate
	 * strings with compile errors.
	 * @param node The current node.
	 * @param str The string of the node's value.
	 * @return The StringBuilder with the given node's value
	 * appended.
	 */
	private StringBuilder handleCast(Expression node, String str) {
		if (node instanceof CastExpression) {
			CastExpression cast = (CastExpression)node;
			sb.append("(");
			flatten(cast.getType());
			sb.append(")");
			return sb.append(str);
		} else
			return sb.append(str);
	}

}
