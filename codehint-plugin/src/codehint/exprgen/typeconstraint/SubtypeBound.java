package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint that matches supertypes of the given type.
 */
public class SubtypeBound extends SingleTypeConstraint {

	/**
	 * Creates a constraint that matches supertypes of the given type.
	 * @param subtypeBound The constraint's type.
	 */
	public SubtypeBound(IJavaType subtypeBound) {
		super(subtypeBound);
	}

	/**
	 * Gets the type for which this constraint matches all supertypes.
	 * @return The type for which this constraint matches all supertypes.
	 */
	public IJavaType getSubtypeBound() {
		return typeConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(typeConstraint, type);
	}

	@Override
	public String toString() {
		return ">=" + typeConstraint.toString();
	}

}
