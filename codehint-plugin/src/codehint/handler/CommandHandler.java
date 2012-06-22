package codehint.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import codehint.utils.EclipseUtils;

/**
 * Superclass for our handlers.  This collects information
 * about the command, such as the selected item, and
 * allows its subclasses to specialize the exact behavior.
 */
public abstract class CommandHandler extends AbstractHandler {
	
	public abstract void handle(IVariable variable, String path, Shell shell);
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IStructuredSelection selection = (IStructuredSelection)HandlerUtil.getActiveMenuSelection(event);
		Object firstElement = selection.getFirstElement();
		if (firstElement instanceof IVariable) {
			IVariable variable = (IVariable)firstElement;
			Shell shell = HandlerUtil.getActiveShell(event);
			String path = getPath((TreeSelection)selection);
			handle(variable, path, shell);
		}
		return null;
	}
    
    /**
     * Gets the full path of the selected variable (i.e.,
     * including the full field access path).  This seems
     * to be the only way to get this information.  Later
     * on, once we only have the IVariable, we can only
     * get the actual value of the enclosing object, not
     * the variable used to access it.
     */
    private static String getPath(TreeSelection selection) {
    	StringBuilder sb = new StringBuilder();
    	TreePath[] paths = selection.getPaths();
    	assert paths.length == 1;  // Our selections should only have one path, right?
    	TreePath path = paths[0];
    	for (int i = 0; i < path.getSegmentCount(); i++) {
    		IJavaVariable variable = (IJavaVariable)path.getSegment(i);
    		if (variable instanceof IJavaFieldVariable)
    			sb.append(".");
    		try {
				sb.append(variable.getName());
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
    	}
    	return sb.toString();
    }
    
    protected static Matcher getInitialConditionFromCurLine(Pattern pattern, IJavaStackFrame stack) throws DebugException {
    	try {
			int lineNum = stack.getLineNumber() - 1;
			IDocument document = EclipseUtils.getDocument();
			String fullCurLine = document.get(document.getLineOffset(lineNum), document.getLineLength(lineNum));
       		Matcher matcher = pattern.matcher(fullCurLine);
       		if(matcher.matches()) {
       			return matcher;
       		} else
       			return null;
    	} catch (BadLocationException e) {
			throw new RuntimeException(e);
		}
    	
    }

}
