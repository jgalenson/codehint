package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * The trivial type constraint that accepts anything.
 */
public class UnknownConstraint extends TypeConstraint {
	
	private static UnknownConstraint unknownConstraint;
	
	private UnknownConstraint() { }
	
	/**
	 * Creates a type constraint that accepts anything.
	 * @return a type constraint that accepts anything.
	 */
	public static UnknownConstraint getUnknownConstraint() {
		if (unknownConstraint == null)
			unknownConstraint = new UnknownConstraint();
		return unknownConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		return true;
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[0];
	}

}
