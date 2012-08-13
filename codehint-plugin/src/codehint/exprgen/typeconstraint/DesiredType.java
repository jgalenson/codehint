package codehint.exprgen.typeconstraint;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint that matches exactly the given type.
 */
public class DesiredType extends SingleTypeConstraint {
	
	/**
	 * Creates a type constraint that matches exactly the given type.
	 * @param desiredType The desired type.
	 */
	public DesiredType(IJavaType desiredType) {
		super(desiredType);
	}
	
	/**
	 * Gets the desired type.
	 * @return The type that fulfills this constraint.
	 */
	public IJavaType getDesiredType() {
		return typeConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		if (type == null)
			return typeConstraint == null || typeConstraint instanceof IJavaReferenceType;
		if (typeConstraint == null)
			return type instanceof IJavaReferenceType;
		return typeConstraint.equals(type); // subtypeChecker.isSubtypeOf(desiredType, curType);
	}

	@Override
	public String toString() {
		try {
			return "=" + typeConstraint == null ? "null" : typeConstraint.getName();
		} catch (DebugException e) {
			e.printStackTrace();
			return "=" + typeConstraint.toString();
		}
	}

}
