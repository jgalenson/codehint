package codehint;

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
	
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	if (EclipseUtils.isPrimitive(variable))
    		demonstratePrimitiveValue(variable, path, shell);
    	else
    		demonstrateObjectValue(variable, path, shell);
    }
    
    private static void demonstratePrimitiveValue(IVariable variable, String path, Shell shell) {
    	try {
	    	String stringValue = EclipseUtils.getExpression(path, shell, "", null);
	    	if (stringValue != null) {
	        	IJavaValue demonstrationValue = EclipseUtils.evaluate(stringValue);
	        	Property property = Property.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue));
	        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, demonstrationValue, shell);
	    	}
        } catch (DebugException e) {
            JDIDebugUIPlugin.errorDialog(shell, ActionMessages.JavaPrimitiveValueEditor_2, ActionMessages.JavaPrimitiveValueEditor_3, e); // 
        }
    }

    private static void demonstrateObjectValue(IVariable variable, String path, Shell shell) {
        try {
            String result = EclipseUtils.getExpression(path, shell, "", null);
        	if (result != null) {
            	Property property = Property.fromObject(result);
            	IJavaValue value = EclipseUtils.evaluate(result);
                Synthesizer.synthesizeAndInsertExpressions(variable, path, property, value, shell);
        	}
        } catch (DebugException e) {
            handleException(e);
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
