package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Field;

import codehint.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;

public class FieldConstraint extends TypeConstraint {
	
	private final String fieldName;
	private final TypeConstraint fieldConstraint;
	
	public FieldConstraint(String fieldName, TypeConstraint fieldConstraint) {
		this.fieldName = fieldName;
		this.fieldConstraint = fieldConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		for (Field field: ExpressionGenerator.getFields(type)) {
			if (fieldName == null || field.name().equals(fieldName))
				return fieldConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType(field.typeName(), target), subtypeChecker, target);
		}
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return new IJavaType[] { EclipseUtils.getFullyQualifiedType("java.lang.Object", target) };
	}

	@Override
	public String toString() {
		return "Object with field named " + fieldName + " of type " + fieldConstraint.toString();
	}

}
