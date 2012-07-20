package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.property.ObjectValueProperty;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public class ObjectValuePropertyDialog extends ValuePropertyDialog {
	
	private final IJavaStackFrame stack;

	public ObjectValuePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, String initialValue, String extraMessage) {
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
    			return ObjectValueProperty.fromObject(propertyText, demonstrationValue);
    		} catch (EvaluationError e) {
				throw e;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
