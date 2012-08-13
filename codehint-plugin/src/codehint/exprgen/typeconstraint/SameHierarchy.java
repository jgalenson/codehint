package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint that matches subtypes or supertypes of the given type.
 */
public class SameHierarchy extends SingleTypeConstraint {

	/**
	 * Creates a constraint that matches subtypes or supertypes of the given type.
	 * @param targetType The constraint's type.
	 */
	public SameHierarchy(IJavaType targetType) {
		super(targetType);
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(type, typeConstraint) || subtypeChecker.isSubtypeOf(typeConstraint, type);
	}

	@Override
	public String toString() {
		return "comparable to " + typeConstraint.toString();
	}

}
