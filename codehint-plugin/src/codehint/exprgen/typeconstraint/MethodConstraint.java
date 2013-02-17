package codehint.exprgen.typeconstraint;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A constraint that ensures that a type has a method with
 * the given name (or any names if the given name is null)
 * and whose return type and arguments meet the given constraints.
 */
public class MethodConstraint extends TypeConstraint {
	
	private final String methodName;
	private final TypeConstraint methodConstraint;
	private final ArrayList<TypeConstraint> argConstraints;
	private final boolean isHandlingSideEffects;

	/**
	 * Creates a constraint that matches types that have methods
	 * of the given name, return type, and argument types.
	 * @param methodName The name of the method.  This can be null, in
	 * which case the constraint accepts methods with any name.
	 * @param methodConstraint The type constraint of the method's return.
	 * @param argConstraints The type constraints of the method's arguments.
	 * This can be null, in which case the constraint accepts methods
	 * with any number and type of arguments.
	 * @param isHandlingSideEffects Whether we are handling side effects.
	 */
	public MethodConstraint(String methodName, TypeConstraint methodConstraint, ArrayList<TypeConstraint> argConstraints, boolean isHandlingSideEffects) {
		this.methodName = methodName;
		this.methodConstraint = methodConstraint;
		this.argConstraints = argConstraints;
		this.isHandlingSideEffects = isHandlingSideEffects;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		// Check each method of the given type to see if it has the desired name and meets the desired constraints.
		for (Method method: ExpressionGenerator.getMethods(type, isHandlingSideEffects))
			if ((methodName == null || method.name().equals(methodName))
					&& MethodNameConstraint.fulfillsArgConstraints(method, argConstraints, stack, target, subtypeChecker, typeCache)
					&& (!"void".equals(method.returnTypeName()) && methodConstraint.isFulfilledBy(EclipseUtils.getTypeAndLoadIfNeededAndExists(method.returnTypeName(), stack, target, typeCache), subtypeChecker, typeCache, stack, target)))
					return true;
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { EclipseUtils.getFullyQualifiedType("java.lang.Object", stack, target, typeCache) };
	}

	@Override
	public String toString() {
		return "Object with method named " + methodName + " of type " + methodConstraint.toString()+ " and args " + (argConstraints == null ? "null" : argConstraints.toString());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((argConstraints == null) ? 0 : argConstraints.hashCode());
		result = prime * result + ((methodConstraint == null) ? 0 : methodConstraint.hashCode());
		result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
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
		MethodConstraint other = (MethodConstraint) obj;
		if (argConstraints == null) {
			if (other.argConstraints != null)
				return false;
		} else if (!argConstraints.equals(other.argConstraints))
			return false;
		if (methodConstraint == null) {
			if (other.methodConstraint != null)
				return false;
		} else if (!methodConstraint.equals(other.methodConstraint))
			return false;
		if (methodName == null) {
			if (other.methodName != null)
				return false;
		} else if (!methodName.equals(other.methodName))
			return false;
		return true;
	}

}
