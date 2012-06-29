package codehint.exprgen.typeconstraint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Field;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;
import codehint.utils.Utils;

public class FieldNameConstraint extends NameConstraint {
	
	private final TypeConstraint expressionConstraint;
	private final TypeConstraint fieldConstraint;
	
	public FieldNameConstraint(TypeConstraint expressionConstraint, TypeConstraint fieldConstraint) {
		this.expressionConstraint = expressionConstraint;
		this.fieldConstraint = fieldConstraint;
	}
	
	public Map<String, ArrayList<Field>> getFields(IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker) {
		try {
			IJavaType[] receiverTypes = expressionConstraint.getTypes(target);
			Map<String, ArrayList<Field>> fieldsByType = new HashMap<String, ArrayList<Field>>(receiverTypes.length);
			for (IJavaType receiverType: receiverTypes) {
				String typeName = receiverType.getName();
				for (Field field: ExpressionGenerator.getFields(receiverType))
					if (fieldFulfills(subtypeChecker, stack, target, field))
						Utils.addToMap(fieldsByType, typeName, field);
			}
			return fieldsByType;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaStackFrame stack, IJavaDebugTarget target) {
		if (!expressionConstraint.isFulfilledBy(type, subtypeChecker, stack, target))
			return false;
		for (Field field: ExpressionGenerator.getFields(type))
			if (fieldFulfills(subtypeChecker, stack, target, field))
				return true;
		return false;
	}

	private boolean fieldFulfills(SubtypeChecker subtypeChecker, IJavaStackFrame stack, IJavaDebugTarget target, Field field) {
		return (legalNames == null || legalNames.contains(field.name())) && fieldConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType(field.typeName(), target), subtypeChecker, stack, target);
	}

	@Override
	public String toString() {
		return "Field with receiver " + expressionConstraint.toString() + " and result " + fieldConstraint.toString();
	}

}
