package codehint.exprgen.typeconstraint;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

public class DesiredType extends SingleTypeConstraint {
	
	public DesiredType(IJavaType desiredType) {
		super(desiredType);
	}
	
	public IJavaType getDesiredType() {
		return typeConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		if (type == null)
			return typeConstraint instanceof IJavaReferenceType;
		if (typeConstraint == null)
			return type instanceof IJavaReferenceType;
		return typeConstraint.equals(type); // subtypeChecker.isSubtypeOf(desiredType, curType);
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { typeConstraint };
	}

	@Override
	public String toString() {
		try {
			return "=" + typeConstraint.getName();
		} catch (DebugException e) {
			e.printStackTrace();
			return "=" + typeConstraint.toString();
		}
	}

}
