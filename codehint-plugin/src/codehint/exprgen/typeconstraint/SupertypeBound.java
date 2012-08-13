package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint that matches subtypes of the given type.
 */
public class SupertypeBound extends SingleTypeConstraint {
	
	/**
	 * Creates a constraint that matches subtypes of the given type.
	 * @param supertypeBound The constraint's type.
	 */
	public SupertypeBound(IJavaType supertypeBound) {
		super(supertypeBound);
	}
	
	/**
	 * Gets the type for which this constraint matches all subtypes.
	 * @return The type for which this constraint matches all subtypes.
	 */
	public IJavaType getSupertypeBound() {
		return typeConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(type, typeConstraint);// || subtypeChecker.isSubtypeOf(supertypeBound, type);
	}

	@Override
	public String toString() {
		return "<=" + typeConstraint.toString();
	}

}
