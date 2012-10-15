package codehint.expreval;

import java.util.ArrayList;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaValue;

import com.sun.jdi.Method;

import codehint.exprgen.ExpressionMaker;
import codehint.property.ASTFlattener;
import codehint.utils.EclipseUtils;
import codehint.utils.Pair;

/**
 * Gets the String representation of a node, substituting in
 * values for its evaluated children when possible.
 */
public class ValueFlattener extends ASTFlattener {
	
	private final Map<String, Integer> temporaries;
	private final ArrayList<Pair<String, String>> newTemporaries;
	
	public ValueFlattener(Map<String, Integer> temporaries) {
		this.temporaries = temporaries;
		this.newTemporaries = new ArrayList<Pair<String, String>>();
	}
	
	@Override
	protected void flatten(Expression node, StringBuilder sb) {
		try {
			IJavaValue value = ExpressionMaker.getExpressionValue(node);
			if (value != null) {
				if (value instanceof IJavaPrimitiveValue) {
					String str = value.toString();
					String sig = value.getSignature();
					if ("C".equals(sig))  // Wrap characters in single quotes.
						sb.append('\'').append(str).append('\'');
					else if ("J".equals(sig))  // A long can be larger than an integer, so we must ensure that Java knows this is a long literal.
						sb.append(str).append('L');
					else if ("F".equals(sig))
						sb.append(str).append('f');
					else if ("D".equals(sig))
						sb.append(str).append('d');
					else
						sb.append(str);
					return;
				} else if (value.isNull()) {
					handleCast(node, value.toString(), sb);
					return;
				} else if (value instanceof IJavaObject && "Ljava/lang/String;".equals(value.getSignature())) {
					String str = value.toString();
					handleCast(node, str.replaceAll("[\n]", "\\\\n"), sb);  // Replace newlines.
					return;
				}
			}
			super.flatten(node, sb);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
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
	private void handleCast(Expression node, String str, StringBuilder sb) {
		if (node instanceof CastExpression) {
			CastExpression cast = (CastExpression)node;
			sb.append("(");
			flatten(cast.getType(), sb);
			sb.append(")");
			sb.append(str);
		} else
			sb.append(str);
	}

	@Override
	protected void flatten(MethodInvocation node, StringBuilder sb) {
		if (node.getParent() != null) {  // Do not replace a top-level call with a temporary.
			// Recursively replace the children.
			StringBuilder childSB = new StringBuilder();
			super.flatten(node, childSB);
			String toString = childSB.toString();
			if (temporaries.containsKey(toString)) {
				sb.append("_$tmp").append(temporaries.get(toString));
				return;
			} else {
				Method method = ExpressionMaker.getMethod(node);
				if (method != null) {  // The method should only be null during refinement.
					String typeStr = EclipseUtils.sanitizeTypename(method.returnTypeName());
					sb.append("_$tmp").append(temporaries.size() + newTemporaries.size());
					newTemporaries.add(new Pair<String, String>(toString, typeStr));
					return;
				}
			}
		}
		super.flatten(node, sb);
	}
	
	public ArrayList<Pair<String, String>> getNewTemporaries() {
		return newTemporaries;
	}

}
