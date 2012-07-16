package codehint.handler;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.dialogs.LambdaPropertyDialog;
import codehint.dialogs.SynthesisDialog;
import codehint.property.LambdaProperty;
import codehint.property.Property;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;

public class DemonstrateLambdaPropertyHandler extends CommandHandler {
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
	    	Property lastCrashedProperty = Synthesizer.getLastCrashedProperty(path);
	    	String initValue = lastCrashedProperty instanceof LambdaProperty && !(lastCrashedProperty instanceof ValueProperty) ? lastCrashedProperty.toString() : "";
	    	IJavaType varType = ((IJavaVariable)variable).getJavaType();
	    	String varTypeName = EclipseUtils.sanitizeTypename(varType.getName());
	    	IJavaStackFrame stack = EclipseUtils.getStackFrame();
	    	SynthesisDialog dialog = new SynthesisDialog(shell, path, varTypeName, varType, stack, new LambdaPropertyDialog(path, varTypeName, varType, stack, initValue, null), new SynthesisWorker(path, varType, stack));
	    	Synthesizer.synthesizeAndInsertExpressions(variable, path, dialog, stack, false);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

}
