package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Field;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

public class FieldConstraint extends TypeConstraint {
	
	private final String fieldName;
	private final TypeConstraint fieldConstraint;
	
	public FieldConstraint(String fieldName, TypeConstraint fieldConstraint) {
		this.fieldName = fieldName;
		this.fieldConstraint = fieldConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		for (Field field: ExpressionGenerator.getFields(type)) {
			if (fieldName == null || field.name().equals(fieldName))
				return fieldConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType(field.typeName(), target, typeCache), subtypeChecker, typeCache, stack, target);
		}
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { EclipseUtils.getFullyQualifiedType("java.lang.Object", target, typeCache) };
	}

	@Override
	public String toString() {
		return "Object with field named " + fieldName + " of type " + fieldConstraint.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fieldConstraint == null) ? 0 : fieldConstraint.hashCode());
		result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
		FieldConstraint other = (FieldConstraint) obj;
		if (fieldConstraint == null) {
			if (other.fieldConstraint != null)
				return false;
		} else if (!fieldConstraint.equals(other.fieldConstraint))
			return false;
		if (fieldName == null) {
			if (other.fieldName != null)
				return false;
		} else if (!fieldName.equals(other.fieldName))
			return false;
		return true;
	}

}
