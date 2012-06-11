package codehint.exprgen.typeconstraint;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Field;

import codehint.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;

public class FieldNameConstraint extends NameConstraint {
	
	private final TypeConstraint expressionConstraint;
	private final TypeConstraint fieldConstraint;
	
	public FieldNameConstraint(TypeConstraint expressionConstraint, TypeConstraint fieldConstraint) {
		this.expressionConstraint = expressionConstraint;
		this.fieldConstraint = fieldConstraint;
	}
	
	public ArrayList<Field> getFields(IJavaDebugTarget target, SubtypeChecker subtypeChecker) {
		ArrayList<Field> fields = new ArrayList<Field>();
		for (IJavaType receiverType: expressionConstraint.getTypes(target))
			for (Field field: ExpressionGenerator.getFields(receiverType))
				if (fieldFulfills(subtypeChecker, target, field))
					fields.add(field);
		return fields;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		if (!expressionConstraint.isFulfilledBy(type, subtypeChecker, target))
			return false;
		for (Field field: ExpressionGenerator.getFields(type))
			if (fieldFulfills(subtypeChecker, target, field))
				return true;
		return false;
	}

	private boolean fieldFulfills(SubtypeChecker subtypeChecker, IJavaDebugTarget target, Field field) {
		return (legalNames == null || legalNames.contains(field.name())) && fieldConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType(field.typeName(), target), subtypeChecker, target);
	}

	@Override
	public String toString() {
		return "Field with receiver " + expressionConstraint.toString() + " and result " + fieldConstraint.toString();
	}

}
