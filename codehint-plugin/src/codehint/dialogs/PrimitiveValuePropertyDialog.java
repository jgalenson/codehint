package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.property.PrimitiveValueProperty;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public class PrimitiveValuePropertyDialog extends ValuePropertyDialog {
	
	private final IJavaStackFrame stack;

	public PrimitiveValuePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, String initialValue, String extraMessage) {
		super(varName, varTypeName, stack, initialValue, extraMessage);
		this.stack = stack;
	}

	@Override
	public Property computeProperty(String propertyText) {
		if (propertyText == null)
			return null;
		else {
    		try {
		    	IJavaValue demonstrationValue = EclipseUtils.evaluate(propertyText, stack);
		    	return PrimitiveValueProperty.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue, stack), demonstrationValue);
    		} catch (EvaluationError e) {
				throw e;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
