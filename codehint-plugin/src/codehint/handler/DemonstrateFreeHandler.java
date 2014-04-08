package codehint.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.ui.handlers.HandlerUtil;

import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.dialogs.InitialSynthesisDialog;
import codehint.dialogs.StatePropertyDialog;
import codehint.utils.EclipseUtils;

public class DemonstrateFreeHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IJavaStackFrame stack = EclipseUtils.getStackFrame();
		if (stack == null) {
			EclipseUtils.showError("Error", "Please start debugging.", null);
			return null;
		}
		InitialSynthesisDialog dialog = new InitialSynthesisDialog(HandlerUtil.getActiveShell(event), null, null, stack, new StatePropertyDialog(null, stack, "", null), new SynthesisWorker(null, null));
		Synthesizer.synthesizeAndInsertStatements(null, null, dialog, stack, false);
		return null;
	}

}
