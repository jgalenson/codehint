package codehint.handler;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.dialogs.InitialSynthesisDialog;
import codehint.dialogs.LambdaPropertyDialog;
import codehint.utils.EclipseUtils;

public class DemonstrateLambdaPropertyHandler extends CommandHandler {
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
	    	IJavaType varType = ((IJavaVariable)variable).getJavaType();
	    	String varTypeName = EclipseUtils.sanitizeTypename(varType.getName());
	    	IJavaStackFrame stack = EclipseUtils.getStackFrame();
	    	InitialSynthesisDialog dialog = new InitialSynthesisDialog(shell, varTypeName, varType, stack, new LambdaPropertyDialog(path, varTypeName, varType, stack, "", null), new SynthesisWorker(path, varType));
	    	Synthesizer.synthesizeAndInsertExpressions(variable, path, dialog, stack, false);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

}
