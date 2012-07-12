package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.swt.widgets.Shell;

import codehint.Synthesizer;
import codehint.Synthesizer.DialogWorker;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.property.ObjectValueProperty;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public class ObjectValuePropertyDialog extends ValuePropertyDialog {
	
	private final IJavaStackFrame stack;

	public ObjectValuePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, Shell shell, String initialValue, String extraMessage, DialogWorker worker) {
		super(varName, varTypeName, stack, shell, initialValue, extraMessage, worker);
		this.stack = stack;
	}

	@Override
	public Property computeProperty() {
		String pdspecText = getPdspecText();
		if (pdspecText == null)
			return null;
		else {
    		try {
    			IJavaValue demonstrationValue = EclipseUtils.evaluate(pdspecText, stack);
    			return ObjectValueProperty.fromObject(pdspecText, demonstrationValue);
    		} catch (EvaluationError e) {
		    	Synthesizer.setLastCrashedInfo(varName, ObjectValueProperty.fromObject(pdspecText, null), null);
				throw e;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
