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
import codehint.exprgen.TypeCache;
import codehint.utils.Utils;

/**
 * A constraint on the receiver of a field access.
 */
public class FieldNameConstraint extends NameConstraint {
	
	private final TypeConstraint expressionConstraint;
	private final TypeConstraint fieldConstraint;
	
	/**
	 * Creates a constraint that ensures that the given type fulfills
	 * the given expression constraint and has a field of a legal
	 * name whose type meets the given field constraint.
	 * @param expressionConstraint The constraint on the receiver.
	 * @param fieldConstraint The constraint on the field's type.
	 */
	public FieldNameConstraint(TypeConstraint expressionConstraint, TypeConstraint fieldConstraint) {
		this.expressionConstraint = expressionConstraint;
		this.fieldConstraint = fieldConstraint;
	}
	
	/**
	 * Gets the fields that can satisfy the given constraint.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @param subtypeChecker the subtype checker.
	 * @param typeCache The type cache.
	 * @return A mapping from the type of the receiving object to
	 * a list of those of its fields that satisfy this constraint.
	 */
	public Map<String, ArrayList<Field>> getFields(IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		try {
			IJavaType[] receiverTypes = expressionConstraint.getTypes(stack, target, typeCache);
			Map<String, ArrayList<Field>> fieldsByType = new HashMap<String, ArrayList<Field>>(receiverTypes.length);
			for (IJavaType receiverType: receiverTypes) {
				String typeName = receiverType.getName();
				for (Field field: ExpressionGenerator.getFields(receiverType))
					if (fieldFulfills(field, stack, target, subtypeChecker, typeCache))
						Utils.addToMap(fieldsByType, typeName, field);
			}
			return fieldsByType;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		if (!expressionConstraint.isFulfilledBy(type, subtypeChecker, typeCache, stack, target))
			return false;
		for (Field field: ExpressionGenerator.getFields(type))
			if (fieldFulfills(field, stack, target, subtypeChecker, typeCache))
				return true;
		return false;
	}

	/**
	 * Ensures that the given field satisfies this constraint.
	 * @param field The field to check.
	 * @param stack The stack frame.
	 * @param target The debug target.
	 * @param subtypeChecker the subtype checker.
	 * @param typeCache The type cache.
	 * @return Whether the given field satisfies this constraint.
	 */
	private boolean fieldFulfills(Field field, IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		return (legalNames == null || legalNames.contains(field.name())) && fieldConstraint.isFulfilledBy(EclipseUtils.getTypeAndLoadIfNeeded(field.typeName(), stack, target, typeCache), subtypeChecker, typeCache, stack, target);
	}

	@Override
	public String toString() {
		return "Field with receiver " + expressionConstraint.toString() + " and result " + fieldConstraint.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((expressionConstraint == null) ? 0 : expressionConstraint.hashCode());
		result = prime * result + ((fieldConstraint == null) ? 0 : fieldConstraint.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		FieldNameConstraint other = (FieldNameConstraint) obj;
		if (expressionConstraint == null) {
			if (other.expressionConstraint != null)
				return false;
		} else if (!expressionConstraint.equals(other.expressionConstraint))
			return false;
		if (fieldConstraint == null) {
			if (other.fieldConstraint != null)
				return false;
		} else if (!fieldConstraint.equals(other.fieldConstraint))
			return false;
		return true;
	}

}
