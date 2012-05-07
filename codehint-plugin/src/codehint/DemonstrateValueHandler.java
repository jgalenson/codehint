package codehint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.actions.ActionMessages;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.progress.UIJob;

import com.sun.jdi.InvalidTypeException;

public class DemonstrateValueHandler extends CommandHandler {


    private final static Pattern pattern = Pattern.compile("\\s*CodeHint.value\\((\\w*)\\s*,\\s*(.*)\\);\\s*\\r?\\n\\s*");
	
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		Matcher matcher = getInitialConditionFromCurLine(variable, pattern);
    		String initValue = null;
    		if (matcher != null) {
    			if (!matcher.group(1).equals(variable.getName())) {
    				EclipseUtils.showError("Illegal variable.", "The first argument to the value method, " + matcher.group(1) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
    				return;
    			}
    			initValue = matcher.group(2);
    		}
	    	if (EclipseUtils.isPrimitive(variable))
	    		demonstratePrimitiveValue(variable, path, initValue, shell);
	    	else
	    		demonstrateObjectValue(variable, path, initValue, shell);
        } catch (DebugException e) {
        	throw new RuntimeException(e);
        }
    }
    
    private static void demonstratePrimitiveValue(IVariable variable, String path, String initValue, Shell shell) throws DebugException {
    	String stringValue = EclipseUtils.getExpression(path, shell, initValue, null);
    	if (stringValue != null) {
        	IJavaValue demonstrationValue = EclipseUtils.evaluate(stringValue);
        	LambdaProperty property = LambdaProperty.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue));
        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, demonstrationValue, shell, initValue != null);
    	}
    }

    private static void demonstrateObjectValue(IVariable variable, String path, String initValue, Shell shell) throws DebugException {
        String result = EclipseUtils.getExpression(path, shell, initValue, null);
    	if (result != null) {
    		LambdaProperty property = LambdaProperty.fromObject(result);
        	IJavaValue value = EclipseUtils.evaluate(result);
            Synthesizer.synthesizeAndInsertExpressions(variable, path, property, value, shell, initValue != null);
    	}
    }

    // TODO: Remove unneeded code, integrate evaluation, etc.

    /**
     * Evaluates the given expression and sets the given variable's value
     * using the result.
     * 
     * @param variable the variable whose value should be set
     * @param expression the expression to evaluate
     * @throws DebugException if an exception occurs evaluating the expression
     *  or setting the variable's value
     */
    protected static void setValue(final IVariable variable, final String expression){
        UIJob job = new UIJob("Setting Variable Value"){ //$NON-NLS-1$
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {
					IValue newValue = EclipseUtils.evaluate(expression);
					if (newValue != null) {
						variable.setValue(newValue);
					} else {
						variable.setValue(expression);
					}
				} catch (DebugException de) {
					handleException(de);
				}
				return Status.OK_STATUS;
			}
        };
        job.setSystem(true);
        job.schedule();
    }

    /**
     * Handles the given exception, which occurred during edit/save.
     */
    protected static void handleException(DebugException e) {
        Throwable cause = e.getStatus().getException();
        if (cause instanceof InvalidTypeException) {
            IStatus status = new Status(IStatus.ERROR, JDIDebugUIPlugin.getUniqueIdentifier(), IDebugUIConstants.INTERNAL_ERROR, cause.getMessage(), null);
            JDIDebugUIPlugin.statusDialog(ActionMessages.JavaObjectValueEditor_3, status);
        } else {
            JDIDebugUIPlugin.statusDialog(e.getStatus()); 
        }
    }

}
