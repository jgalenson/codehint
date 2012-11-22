package codehint.handler;

import java.util.ArrayList;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import codehint.Synthesizer;
import codehint.utils.EclipseUtils;

/**
 * Automatically starts synthesis by setting a breakpoint
 * and then launching the debugger.
 */
public class SynthesisStarter extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			if (isRunning()) {
				EclipseUtils.showError("Error", "Please terminate the running launches.", null);
				return null;
			}
			ITextEditor editor = getTextEditor(event);
			if (editor == null) {
				EclipseUtils.showError("Error", "Please select a line in the editor.", null);
				return null;
			}
			IFile file = getFile(editor);
			ILaunchConfiguration config = getLaunchConfiguration(file);
			if (config == null) {
				EclipseUtils.showError("Error", "I cannot find a unique launch configuration for this file.", null);
				return null;
			}
			int line = getLineNumber(editor);
			// Get the closest line that can have a breakpoint (e.g., is executable).
			CompilationUnit unit = SharedASTProvider.getAST(JavaUI.getWorkingCopyManager().getWorkingCopy(editor.getEditorInput()), SharedASTProvider.WAIT_YES, null);
			ValidBreakpointLocationLocator bpLoc = new ValidBreakpointLocationLocator(unit, line, false, false);
			unit.accept(bpLoc);
			line = bpLoc.getLineLocation();
			assert line != -1;
			String typename = bpLoc.getFullyQualifiedTypeName();
			// TODO: Replace the above internal API, perhaps with something like the below, which programatically causes a toggle breakpoint action (but doesn't work correctly). 
			//editor.getAction(ITextEditorActionConstants.RULER_DOUBLE_CLICK).run();
			// Add a breakpoint at this line if one does not already exist.
			if (JDIDebugModel.lineBreakpointExists(file, typename, line) == null)
				Synthesizer.addBreakpoint(file, typename, line);
			// Launch the program in debug mode.
			DebugUITools.launch(config, "debug");
			return null;
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Checks whether there is a launch running.
	 * @return Whether there is a launch running.
	 */
	private static boolean isRunning() {
		for (ILaunch launch: DebugPlugin.getDefault().getLaunchManager().getLaunches())
			if (!launch.isTerminated())
				return true;
		return false;
	}
	
	/**
	 * Gets the selected text editor, or null if
	 * it is not selected.
	 * @param event The execution event.
	 * @return The selected text editor, or null
	 * if the text editor is not selected.
	 */
	private static ITextEditor getTextEditor(ExecutionEvent event) {
		Object part = HandlerUtil.getActivePart(event);
		return part instanceof ITextEditor ? (ITextEditor)part : null;  // Return null if the text editor is not selected.
	}
	
	/**
	 * Gets the line number of the start of the current selection.
	 * @param editor The text editor.
	 * @return The line number of the start of the selection.
	 */
	private static int getLineNumber(ITextEditor editor) {
		ISelection selection = editor.getSelectionProvider().getSelection();
		if (selection instanceof ITextSelection) {
			ITextSelection textSelection = (ITextSelection)selection;
			return textSelection.getStartLine() + 1;
		}
		throw new RuntimeException("Cannot get line number.");
	}

	/**
	 * Gets the file being edited by the given text editor.
	 * @param editor The text editor.
	 * @return The file being edited by the text editor.
	 */
	private static IFile getFile(ITextEditor editor) {
		return ((IFileEditorInput)((IEditorPart)editor.getAdapter(IEditorPart.class)).getEditorInput()).getFile();
	}
	
	/*private static String getTypeName(IFile file) {
		// This is hacky and would need to be fixed if I used it.
		String pathName = file.getFullPath().toString();
		return pathName.substring(pathName.indexOf("/src/") + 5, pathName.length() - 5).replace("/", ".");
	}*/
    
    /**
     * Gets the unique existing launch configuration for the given file,
     * or returns null.
     * TODO: I should handle the ambiguity by getting the "default" one.
     * @param file The file that should be launched.
     * @return The unique existing launch configuration for the given file,
     * or null.
     */
	private static ILaunchConfiguration getLaunchConfiguration(IFile file) {
		try {
			String fileName = file.getName().substring(0, file.getName().length() - 5);
			ArrayList<ILaunchConfiguration> configs = new ArrayList<ILaunchConfiguration>();
			for (ILaunchConfiguration config: DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations())
				if (config.getName().equals(fileName))
					configs.add(config);
			if (configs.size() == 1)
				return configs.get(0);
			return null;
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}

}
