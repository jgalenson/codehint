package codehint.expreval;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaValue;

import com.sun.jdi.Method;

import codehint.effects.Effect;
import codehint.exprgen.ExpressionMaker;
import codehint.exprgen.Result;
import codehint.exprgen.StringValue;
import codehint.exprgen.Value;
import codehint.exprgen.ValueCache;
import codehint.property.ASTFlattener;
import codehint.utils.EclipseUtils;
import codehint.utils.Pair;

/**
 * Gets the String representation of a node, substituting in
 * values for its evaluated children when possible.
 */
public class ValueFlattener extends ASTFlattener {

	private final Map<String, Integer> temporaries;
	private final Map<String, Pair<Integer, String>> newTemporaries;
	private final ExpressionMaker expressionMaker;
	private final ValueCache valueCache;
	private Set<Effect> curEffects;
	
	public ValueFlattener(Map<String, Integer> temporaries, ExpressionMaker expressionMaker, ValueCache valueCache) {
		this.temporaries = temporaries;
		this.newTemporaries = new HashMap<String, Pair<Integer, String>>();
		this.expressionMaker = expressionMaker;
		this.valueCache = valueCache;
		this.curEffects = Collections.<Effect>emptySet();
	}
	
	@Override
	protected void flatten(Expression node, StringBuilder sb) {
		try {
			Result result = null;
			Object idObj = ExpressionMaker.getIDOpt(node);
			if (idObj != null)
				result = expressionMaker.getExpressionResult((Integer)idObj, curEffects);
			if (result != null) {
				IJavaValue value = result.getValue().getValue();
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
					Value wrapper = valueCache.getValue(value);
					String str = wrapper instanceof StringValue ? "\"" + ((StringValue)wrapper).getStringValue() + "\"" : value.toString();
					handleCast(node, str.replaceAll("[\n]", "\\\\n"), sb);  // Replace newlines.
					return;
				}
			}
			super.flatten(node, sb);
			if (result != null)
				curEffects = result.getEffects();
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
	 * @param sb The StringBuilder into which we will
	 * append the result.
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
		String toString = node.toString();
		if (temporaries.containsKey(toString)) {
			sb.append("_$tmp").append(temporaries.get(toString));
			return;
		} else if (newTemporaries.containsKey(toString)) {
			sb.append("_$tmp").append(newTemporaries.get(toString).first);
			return;
		} else {
			Method method = null;
			Object idObj = ExpressionMaker.getIDOpt(node);
			if (idObj != null)
				method = expressionMaker.getMethod((Integer)idObj);
			if (method != null) {  // The method should only be null during refinement.
				String typeStr = EclipseUtils.sanitizeTypename(method.returnTypeName());
				int newIndex = temporaries.size() + newTemporaries.size();
				sb.append("_$tmp").append(newIndex);
				newTemporaries.put(toString, new Pair<Integer, String>(newIndex, typeStr));
				return;
			}
		}
		super.flatten(node, sb);
	}
	
	public Map<String, Pair<Integer, String>> getNewTemporaries() {
		return newTemporaries;
	}

}
