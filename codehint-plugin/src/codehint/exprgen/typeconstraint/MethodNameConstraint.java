package codehint.exprgen.typeconstraint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.utils.Utils;

/**
 * A constraint on the receiver of a method call.
 */
public class MethodNameConstraint extends NameConstraint {

	private final TypeConstraint expressionConstraint;
	private final TypeConstraint methodConstraint;
	private final ArrayList<TypeConstraint> argConstraints;

	/**
	 * Creates a constraint that ensures that the given type fulfills
	 * the given expression constraint and has a method of a legal
	 * name whose return type meets the given method constraint
	 * and whose arguments meet the given arg constraints.
	 * @param expressionConstraint The constraint on the receiver.
	 * @param methodConstraint The constraint on the method's type.
	 * @param argConstraints The constraints on the method's arguments.
	 * This can be null, in which case the constraint accepts methods
	 * with any number and type of arguments.
	 */
	public MethodNameConstraint(TypeConstraint expressionConstraint, TypeConstraint methodConstraint, ArrayList<TypeConstraint> argConstraints) {
		this.expressionConstraint = expressionConstraint;
		this.methodConstraint = methodConstraint;
		this.argConstraints = argConstraints;
	}

	/**
	 * Gets the methods that can satisfy the given constraint.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @param subtypeChecker the subtype checker.
	 * @param typeCache The type cache.
	 * @return A mapping from the type of the receiving object to
	 * a list of those of its methods that satisfy this constraint.
	 */
	public Map<String, ArrayList<Method>> getMethods(IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		try {
			IJavaType[] receiverTypes = expressionConstraint.getTypes(stack, target, typeCache);
			Map<String, ArrayList<Method>> methodsByType = new HashMap<String, ArrayList<Method>>(receiverTypes.length);
    		for (IJavaType receiverType: receiverTypes) {
    			String typeName = receiverType.getName();
	    		for (Method method: ExpressionGenerator.getMethods(receiverType))
					if (ExpressionGenerator.isLegalMethod(method, stack.getReferenceType(), false) && methodFulfills(method, stack, target, subtypeChecker, typeCache))
						Utils.addToMap(methodsByType, typeName, method);
    		}
    		return methodsByType;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		if (!expressionConstraint.isFulfilledBy(type, subtypeChecker, typeCache, stack, target))
			return false;
		for (Method method: ExpressionGenerator.getMethods(type))
			if (methodFulfills(method, stack, target, subtypeChecker, typeCache))
				return true;
		return false;
	}

	/**
	 * Ensures that the given method satisfies this constraint.
	 * @param method The method to check.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @param subtypeChecker the subtype checker.
	 * @param typeCache The type cache.
	 * @return Whether the given method satisfies this constraint.
	 */
	private boolean methodFulfills(Method method, IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		return (legalNames == null || legalNames.contains(method.name())) && 
				fulfillsArgConstraints(method, argConstraints, stack, target, subtypeChecker, typeCache)
				&& (!"void".equals(method.returnTypeName()) && methodConstraint.isFulfilledBy(EclipseUtils.getTypeAndLoadIfNeededAndExists(method.returnTypeName(), stack, target, typeCache), subtypeChecker, typeCache, stack, target));
	}

	/**
	 * Ensures that the given method satisfies the given arg constraints.
	 * @param method The method to check.
	 * @param argConstraints The arg constraints.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @param subtypeChecker the subtype checker.
	 * @param typeCache The type cache.
	 * @return Whether the given method satisfies the given arg constraints.
	 */
	public static boolean fulfillsArgConstraints(Method method, ArrayList<TypeConstraint> argConstraints, IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		if (argConstraints == null)
			return true;
		if (method.argumentTypeNames().size() != argConstraints.size())
			return false;
		for (int i = 0; i < argConstraints.size(); i++) {
			TypeConstraint argConstraint = argConstraints.get(i);
			if (argConstraint != null && !argConstraint.isFulfilledBy(EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache), subtypeChecker, typeCache, stack, target))
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "Method with receiver " + expressionConstraint.toString() + " and result " + methodConstraint.toString() + " and args " + argConstraints.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((argConstraints == null) ? 0 : argConstraints.hashCode());
		result = prime * result + ((expressionConstraint == null) ? 0 : expressionConstraint.hashCode());
		result = prime * result + ((methodConstraint == null) ? 0 : methodConstraint.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		MethodNameConstraint other = (MethodNameConstraint) obj;
		if (argConstraints == null) {
			if (other.argConstraints != null)
				return false;
		} else if (!argConstraints.equals(other.argConstraints))
			return false;
		if (expressionConstraint == null) {
			if (other.expressionConstraint != null)
				return false;
		} else if (!expressionConstraint.equals(other.expressionConstraint))
			return false;
		if (methodConstraint == null) {
			if (other.methodConstraint != null)
				return false;
		} else if (!methodConstraint.equals(other.methodConstraint))
			return false;
		return true;
	}

}
