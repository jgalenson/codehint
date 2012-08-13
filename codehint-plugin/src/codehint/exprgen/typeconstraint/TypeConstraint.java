package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint.
 */
public abstract class TypeConstraint {
	
	/**
	 * Checks whether this type constraint is fulfilled by the given type.
	 * @param type The type that we want to check if it fulfills this constraint.
	 * @param subtypeChecker The subtype checker.
	 * @param typeCache The type cache.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @return Whether this type constraint is fulfilled by the given type.
	 */
	public abstract boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target);
	
	/**
	 * Gets the types represented by this constraint.
	 * Note that this is not the set of types that might
	 * fulfill this constraint (which might include all
	 * types); it is simply an approximation.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @param typeCache The type cache.
	 * @return The types represented by this constraint.
	 */
	public abstract IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache);

}
