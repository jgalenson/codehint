package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.swt.widgets.Shell;

import codehint.Synthesizer;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.property.ObjectValueProperty;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public class ObjectValuePropertyDialog extends ValuePropertyDialog {

	public ObjectValuePropertyDialog(String varName, String varTypeName, Shell shell, String initialValue, String extraMessage, boolean getSkeleton) {
		super(varName, varTypeName, shell, initialValue, extraMessage, getSkeleton);
	}

	@Override
	public Property getProperty() {
		String pdspecText = getPdspecText();
		if (pdspecText == null)
			return null;
		else {
    		try {
    			IJavaValue demonstrationValue = EclipseUtils.evaluate(pdspecText);
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
