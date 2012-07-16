package codehint.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.utils.EclipseUtils;
import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.dialogs.StatePropertyDialog;
import codehint.dialogs.SynthesisDialog;
import codehint.property.Property;
import codehint.property.StateProperty;

public class DemonstrateStatePropertyHandler extends CommandHandler {

    public final static Pattern PATTERN = Pattern.compile("\\s*CodeHint.pdspec\\((\\w*)\\s*,\\s*(.*)\\);\\s*\\r?\\n\\s*");
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		IJavaStackFrame stack = EclipseUtils.getStackFrame();
    		handle(variable, path, shell, getInitialConditionFromCurLine(PATTERN, stack), stack);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
	private static void handle(IVariable variable, String path, Shell shell, Matcher matcher, IJavaStackFrame stack) throws DebugException {
		String initValue = null;
		if (matcher != null) {
			if (!matcher.group(1).equals(variable.getName())) {
				EclipseUtils.showError("Illegal variable.", "The first argument to the pdspec method, " + matcher.group(1) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
				return;
			}
			initValue = matcher.group(2);
		} else {
			Property lastCrashedProperty = Synthesizer.getLastCrashedProperty(path);
			initValue = lastCrashedProperty instanceof StateProperty ? lastCrashedProperty.toString() : "";
		}
		IJavaType varType = ((IJavaVariable)variable).getJavaType();
		String varTypeName = EclipseUtils.sanitizeTypename(varType.getName());
		SynthesisDialog dialog = new SynthesisDialog(shell, path, varTypeName, varType, stack, new StatePropertyDialog(path, stack, initValue, null), new SynthesisWorker(path, varType, stack));
    	Synthesizer.synthesizeAndInsertExpressions(variable, path, dialog, stack, matcher != null);
    }

	public static void handleFromText(Matcher matcher, IJavaStackFrame stack) {
    	try {
			String varName = matcher.group(1);
			IVariable var = stack.findVariable(varName);
			handle(var, varName, EclipseUtils.getShell(), matcher, stack);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
