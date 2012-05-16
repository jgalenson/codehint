package codehint;

import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jdt.internal.debug.ui.JDIModelPresentation;
import org.eclipse.jdt.internal.debug.ui.actions.EvaluateAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import codehint.expreval.EvaluationManager;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.property.LambdaProperty;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.StateProperty;
import codehint.property.TypeProperty;

public class EclipseUtils {
    
	/**
	 * Return the project associated with the given stack frame.
	 * (copied from JavaWatchExpressionDelegate)
	 * (copied from JavaObjectValueEditor)
	 * @param javaStackFrame The stack frame
	 * @return the project associate with the given stack frame.
	 */
	public static IJavaProject getProject(IJavaStackFrame javaStackFrame) {
		ILaunch launch = javaStackFrame.getLaunch();
		if (launch == null) {
			return null;
		}
		ISourceLocator locator= launch.getSourceLocator();
		if (locator == null) {
			return null;
		}

		Object sourceElement = locator.getSourceElement(javaStackFrame);
		if (!(sourceElement instanceof IJavaElement) && sourceElement instanceof IAdaptable) {
			sourceElement = ((IAdaptable)sourceElement).getAdapter(IJavaElement.class);
		}
		if (sourceElement instanceof IJavaElement) {
			return ((IJavaElement) sourceElement).getJavaProject();
		}
		return null;
	}
    
    public static String getSignature(IVariable variable) throws DebugException {
        String signature= null;
        IJavaVariable javaVariable = (IJavaVariable) variable.getAdapter(IJavaVariable.class);
        if (javaVariable != null)
            signature = javaVariable.getSignature();
        return signature;
    }
    
    public static boolean isPrimitive(IVariable variable) {
		try {
			String signature = getSignature(variable);
	    	return isPrimitive(signature);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
    public static boolean isPrimitive(String signature) {
    	assert signature != null;
    	return !JDIModelPresentation.isObjectValue(signature);
    }
   	
    // TODO: There must be a better way to do this through Eclipse.
	/**
	 * Rewrites a type name so that it can be executed in code.
	 * For example "Test.Foo foo" compiles but "Test$Foo foo",
	 * the full typename, does not.
	 * @param name The type name.
	 * @return A sanitized version of the given type name.
	 */
   	public static String sanitizeTypename(String name) {
    	return name.replace('$', '.');
   	}
    
    /**
   	 * Returns the current stack frame context, or <code>null</code> if none.
   	 * 
   	 * @return the current stack frame context, or <code>null</code> if none
   	 */
   	public static IJavaStackFrame getStackFrame() {
        IAdaptable adaptable = DebugUITools.getDebugContext();
        if (adaptable != null) {
        	Object x = adaptable.getAdapter(IJavaStackFrame.class);
        	if (x != null)
        		return (IJavaStackFrame)x;
        }
        try {
	   		for (ILaunch launch: DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
	   			for (IThread thread: launch.getDebugTarget().getThreads()) {
	   				IStackFrame[] frames = thread.getStackFrames();
	   				if (frames.length > 0)
	   					return (IJavaStackFrame)frames[0];
	   			}
	   		}
        } catch (DebugException e) {
        	throw new RuntimeException(e);
        }
   		return null;
   	}
    
    private static IEditorPart getActiveEditorPart() {
   		IWorkbench workbench = PlatformUI.getWorkbench();
       	IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
       	return page.getActiveEditor();
    }
    
    public static ITextEditor getActiveTextEditor() {
    	IEditorPart editorPart = getActiveEditorPart();
    	ITextEditor editor = (ITextEditor) editorPart.getAdapter(ITextEditor.class);
    	assert editor != null;
    	return editor;
    }
    
    /**
     * Parses the given string into a Java expression.
     * @param parser The AST parser to use.
     * @param str The string of a Java expression.
     * @return The resulting AST.
     */
    public static ASTNode parseExpr(ASTParser parser, String str) {
    	// We apparently have to manually tell it to use Java 1.5....
		Map<?, ?> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_5, options);
		parser.setCompilerOptions(options);
		// We only want to parse an expression, not a whole program.
		parser.setKind(ASTParser.K_EXPRESSION);
		// Do the actual parsing.
		parser.setSource(str.toCharArray());
		return parser.createAST(null);
    }
    
    /**
     * Returns a string representing the given value this is
     * a legal Java expression.
     * @param value The value whose string representation is desired.
     * @return A string that is the legal Java expression of
     * the given value.
     * @throws DebugException if we cannot get the value.
     */
    public static String javaStringOfValue(IJavaValue value) throws DebugException {
    	if ("C".equals(value.getSignature()))
    		return "'" + value.getValueString() + "'";
    	else if ("Ljava/lang/String;".equals(value.getSignature()))
    		return "\"" + value.getValueString() + "\"";
    	else
    		return value.getValueString();
    }
    
    private static IType getThisType(IJavaProject project, IJavaStackFrame stackFrame) throws DebugException, JavaModelException {
    	String thisTypeName = stackFrame.getDeclaringTypeName();
		IType thisType = project.findType(thisTypeName);
		if (thisType != null && !thisTypeName.contains("$"))  // Do not use anonymous classes (e.g., Foo$1).
			return thisType;
		// The above will fail for anonymous classes (e.g., Foo$1), so we just get the outer part before the $.
		thisTypeName = sanitizeTypename(thisTypeName);
		if (thisTypeName.contains(".")) {
			String outerTypeName = thisTypeName.substring(0, thisTypeName.indexOf('.'));
			return project.findType(outerTypeName);
		}
		return null;
    }
    
    public static LambdaProperty getLambdaProperty(String varName, Shell shell, IJavaType varStaticType, String initialValue, String extraMessage, IJavaStackFrame stackFrame) {
    	try {
    		IJavaProject project = getProject(stackFrame);
    		String varStaticTypeName = varStaticType == null ? null : sanitizeTypename(varStaticType.getName());
    		IType varType = varStaticType == null ? null : getProject(stackFrame).findType(varStaticTypeName);
    		IType thisType = getThisType(project, stackFrame);
    		
	        String title= "Demonstrate property"; 
	        String message= "Demonstrate a property (in the form of a boolean lambda expression) that should hold for " + varName + " after this statement is executed.";
	        if (initialValue == null)
	        	initialValue = getDefaultLambdaArgName(stackFrame) + getDefaultTypeName(varStaticType, project, varType, thisType, varStaticTypeName) + " => ";
	        LambdaPropertyValidator validator= new LambdaPropertyValidator(stackFrame, project, varType, thisType);
	        String stringValue = getDialogResult(title, message, extraMessage, initialValue, validator, "lambda");
	    	if (stringValue != null)
	    		return LambdaProperty.fromPropertyString(stringValue);
	    	else
	    		return null;
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		} catch (JavaModelException e) {
 			throw new RuntimeException(e);
 		}
    }
    
    private static String getDefaultLambdaArgName(IJavaStackFrame stackFrame) throws DebugException {
    	if (stackFrame.findVariable("x") == null)
    		return "x";
    	for (char name = 'a'; name <= 'z'; name++)
        	if (stackFrame.findVariable("" + name) == null)
        		return "" + name;
    	for (int i = 0; true; i++)
        	if (stackFrame.findVariable("x" + i) == null)
        		return "x" + i;
    }
    
    private static String getDefaultTypeName(IJavaType varStaticType, IJavaProject project, IType varType, IType thisType, String varStaticTypeName) throws DebugException {
    	if (varStaticType == null || isPrimitive(varStaticType.getSignature()))
    		return "";
		
		String unqualifiedTypename = getUnqualifiedName(varStaticTypeName);
		if (getValidTypeError(project, varType, thisType, unqualifiedTypename) == null)
			return ": " + unqualifiedTypename;
		else
			return ": " + varStaticTypeName;
    }
    
    private static String getExpression(String varName, Shell shell, String initialValue, String extraMessage) {
        String title= "Demonstrate an expression"; 
        String message= "Demonstrate an expression for " + varName + ".  We will find expressions that evaluate to the same value.";
        ExpressionValidator validator= new ExpressionValidator();
        if (initialValue == null)
        	initialValue = "";
        String stringValue = getDialogResult(title, message, extraMessage, initialValue, validator, "value");
    	return stringValue;
    }
    
    public static PrimitiveValueProperty getPrimitiveValueProperty(String varName, Shell shell, String initialValue, String extraMessage) throws DebugException {
    	String stringValue = getExpression(varName, shell, initialValue, extraMessage);
    	if (stringValue != null) {
    		try {
		    	IJavaValue demonstrationValue = evaluate(stringValue);
		    	return PrimitiveValueProperty.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue), demonstrationValue);
    		}  catch (EvaluationError e) {
		    	Synthesizer.setLastCrashedProperty(varName, PrimitiveValueProperty.fromPrimitive(stringValue, null));
				throw e;
			}
    	} else
    		return null;
    }
    
    public static ObjectValueProperty getObjectValueProperty(String varName, Shell shell, String initialValue, String extraMessage) throws DebugException {
    	String stringValue = getExpression(varName, shell, initialValue, extraMessage);
    	if (stringValue != null) {
    		try {
    			IJavaValue demonstrationValue = evaluate(stringValue);
    			return ObjectValueProperty.fromObject(stringValue, demonstrationValue);
    		} catch (EvaluationError e) {
		    	Synthesizer.setLastCrashedProperty(varName, ObjectValueProperty.fromObject(stringValue, null));
				throw e;
			}
    	} else
    		return null;
    }
    
    public static TypeProperty getTypeProperty(String varName, Shell shell, String varTypeName, String initialValue, String extraMessage, IJavaStackFrame stackFrame) {
    	try {
    		varTypeName = sanitizeTypename(varTypeName);
			IJavaProject project = getProject(stackFrame);
			IType varType = getProject(stackFrame).findType(varTypeName);
    		IType thisType = getThisType(project, stackFrame);
			
			// Default to the unqualified typename if I can.
    		if (initialValue.contains("$"))
    			initialValue = sanitizeTypename(initialValue);
			if (thisType != null && thisType.resolveType(getUnqualifiedName(initialValue)) != null)
				initialValue = getUnqualifiedName(initialValue);
	    	
	    	String title= "Demonstrate a type"; 
	        String message= "Demonstrate a type for " + varName + ".  We will find expressions return that type when evaluated.";
	        TypeValidator validator= new TypeValidator(project, varType, thisType);
	        String typeName = getDialogResult(title, message, extraMessage, initialValue, validator, "type");
	    	if (typeName != null)
	    		return TypeProperty.fromType(typeName);
    		else
    			return null;
    	} catch (JavaModelException e) {
 			throw new RuntimeException(e);
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		}
    }
    
    public static StateProperty getStateProperty(String varName, Shell shell, String initialValue, String extraMessage) {
		String title= "Demonstrate state property"; 
        String message= "Demonstrate a state property that should hold for " + varName + " after this statement is executed.  You may refer to the values of variables after this statement is executed using the prime syntax, e.g., " + varName + "\'";
        StatePropertyValidator validator= new StatePropertyValidator();
        String stringValue = getDialogResult(title, message, extraMessage, initialValue, validator, "state");
    	if (stringValue != null)
    		return StateProperty.fromPropertyString(varName, stringValue);
    	else
    		return null;
    }
    
    private static String getDialogResult(String title, String message, String extraMessage, String initialValue, IInputValidator validator, final String helpContext) {
        if (extraMessage != null)
        	message += System.getProperty("line.separator") + extraMessage;
    	InputDialog dialog= new ModelessInputDialog(null, title, message, initialValue, validator){
        	@Override
			protected Control createDialogArea(Composite parent) {
        		IWorkbench workbench = PlatformUI.getWorkbench();
        		workbench.getHelpSystem().setHelp(parent, Activator.PLUGIN_ID + "." + helpContext);
        		return super.createDialogArea(parent);
        	}
        };
        if (dialog.open() == Window.OK) {
            String stringValue = dialog.getValue();
        	return stringValue;
    	}
        return null;
    }
    
    // Adapted from http://www.eclipse.org/forums/index.php/mv/tree/4336/.
    // TODO: Using this allows the user to do some weird things like continue execution, which can potentially do weird things.
    private static class ModelessInputDialog extends InputDialog
    {
    	public ModelessInputDialog(Shell parentShell, String dialogTitle, String dialogMessage, String initialValue, IInputValidator validator) {
    		super(parentShell, dialogTitle, dialogMessage, initialValue, validator);
    		setBlockOnOpen(false);
    	}

    	@Override
		protected void setShellStyle(int newShellStyle)
    	{
    		int newstyle = newShellStyle & ~SWT.APPLICATION_MODAL; // turn off APPLICATION_MODAL
    		newstyle |= SWT.MODELESS; // turn on MODELESS
    		super.setShellStyle(newstyle);
    	}

    	@Override
		public int open()
    	{
    		int retVal = super.open();
    		pumpMessages(); // this will let the caller wait till OK, Cancel is pressed, but will let the other GUI responsive
    		return retVal; // TODO: Since open() returns immediately, we don't get the real return value.  Specifically, if the user clicks Cancel, we think they clicked OK.
    	}

    	protected void pumpMessages()
    	{
    		Shell shell = getShell();
    		Display display = shell.getDisplay();
    		while (!shell.isDisposed())
    			if (!display.readAndDispatch())
    				display.sleep();
    		display.update();
    	}
    }

    private static class LambdaPropertyValidator implements IInputValidator {
    	
    	private final IJavaStackFrame stackFrame;
    	private final IJavaProject project;
    	private final IType varType;
    	private final IType thisType;
    	
    	public LambdaPropertyValidator(IJavaStackFrame stackFrame, IJavaProject project, IType varType, IType thisType) {
    		this.stackFrame = stackFrame;
    		this.project = project;
    		this.varType = varType;
    		this.thisType = thisType;
    	}
        
        @Override
		public String isValid(String newText) {
        	return LambdaProperty.isLegalProperty(newText, stackFrame, project, varType, thisType);
        }
    }

    private static class ExpressionValidator implements IInputValidator {
    	
        private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
        
        @Override
		public String isValid(String newText) {
        	ASTNode node = EclipseUtils.parseExpr(parser, newText);
        	if (node instanceof CompilationUnit)
        		return "Enter a valid expression: " + ((CompilationUnit)node).getProblems()[0].getMessage();
        	return null;
        }
    }

    /**
     * A validator that ensures that the entered text is a type
     * that is a subtype of the given variable's type.
     */
    private static class TypeValidator implements IInputValidator {
    	
    	private final IJavaProject project;
    	private final IType varType;
    	private final IType thisType;
    	
    	public TypeValidator(IJavaProject project, IType varType, IType thisType) {
    		this.project = project;
    		this.varType = varType;
    		this.thisType = thisType;
    	}
        
        @Override
		public String isValid(String newText) {
        	return getValidTypeError(project, varType, thisType, newText);
        }
    }
    
    public static String getValidTypeError(IJavaProject project, IType varType, IType thisType, String newTypeName) {
    	try {
    		if (varType == null)
    			return "You cannot enter a type for a primitive.";
    		if (thisType == null)
    			return null;
    		String[][] allTypes = thisType.resolveType(newTypeName);
    		if (allTypes == null)
        		return "Please enter a valid type.";
    		if (allTypes.length > 1)
    			return "Please enter an unambiguous type by qualifying it.";
    		String[] typeParts = allTypes[0];
			String fullTypeName = "";
			for (int i = 0; i < typeParts.length; i++) {
				if (i > 0)
					fullTypeName += ".";
				fullTypeName += typeParts[i];
			}
        	IType curType = project.findType(fullTypeName);
			if (curType.newSupertypeHierarchy(null).contains(varType))
				return null;
			return "Please enter a subtype of " + varType.getFullyQualifiedName('.') + ".";
    	} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
    }

    private static class StatePropertyValidator implements IInputValidator {
        
        @Override
		public String isValid(String newText) {
        	return StateProperty.isLegalProperty(newText);
        }
    }
    
   	public static void insertIndentedLineAtCurrentDebugPoint(String text) throws BadLocationException, DebugException {
   		int line = getStackFrame().getLineNumber() - 1;
   		IDocument document = getDocument();
   		
   		int offset = document.getLineOffset(line);
   		int firstchar = offset;
   		while( document.getChar(firstchar) == ' ' ||
   				document.getChar(firstchar) == '\t') {
   			firstchar++;
   		}
   		String indent = document.get(offset, firstchar-offset);
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(firstchar, 0, text + "\n" + indent); //$NON-NLS-1$
   		
   		//Log the change for debugging
   		//System.out.println("Inserting text: \"\"\"" + text + "\"\"\" at " + path + " line " + line + " position " + start + " to " + end);

   	}
   	
   	public static void replaceLineAtCurrentDebugPoint(String newText) throws DebugException, BadLocationException {
   		int line = getStackFrame().getLineNumber() - 1;
   		replaceLine(newText, line);
   	}
   	
   	public static void replaceLine(String newText, int line) throws BadLocationException {
   		IDocument document = getDocument();
   		
   		int offset = document.getLineOffset(line);
   		int firstChar = offset;
   		while (document.getChar(firstChar) == ' ' || document.getChar(firstChar) == '\t')
   			firstChar++;
   		int lastChar = firstChar;
   		while (document.getChar(lastChar) != '\n')
   			lastChar++;
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(firstChar, lastChar - firstChar, newText);
   	}
   	
   	public static IDocument getDocument() {
   		ITextEditor editor = EclipseUtils.getActiveTextEditor();
   		return editor.getDocumentProvider().getDocument(editor.getEditorInput());
   	}

   	// This only seems to work when called from the UI thread (e.g., when called with Display.syncExec).
	public static Shell getShell() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}
   	
   	public static void showError(final String title, String text, Throwable exception) {
   		showErrorOrWarning(title, text, exception, IStatus.ERROR);
   	}
   	
   	public static void showWarning(final String title, String text, Throwable exception) {
   		showErrorOrWarning(title, text, exception, IStatus.WARNING);
   	}
   	
   	private static void showErrorOrWarning(final String title, String text, Throwable exception, int severity) {
   		final IStatus status = new Status(severity, Activator.PLUGIN_ID, Status.OK, text, exception);
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				ErrorDialog.openError(getShell(), title, null, status);
			}
		});
   	}

    /**
     * Evaluates the given snippet. Reports any errors to the user.
     * @param stringValue the snippet to evaluate
     * @return the value that was computed or <code>null</code> if any errors occurred.
     * @throws DebugException 
     */
   	// TODO: Integrate with pbd.expreval code?
    public static IJavaValue evaluate(String stringValue) throws DebugException {
        IJavaStackFrame frame = getStackFrame();
        if (frame != null) {
            IJavaThread thread = (IJavaThread) frame.getThread();
            final IJavaProject project= EclipseUtils.getProject(frame);
            if (project != null) {
                final IEvaluationResult[] results= new IEvaluationResult[1];
                IAstEvaluationEngine engine = org.eclipse.jdt.debug.eval.EvaluationManager.newAstEvaluationEngine(project, (IJavaDebugTarget)thread.getDebugTarget());
                IEvaluationListener listener= new IEvaluationListener() {
                    @Override
					public void evaluationComplete(IEvaluationResult result) {
                        synchronized (project) {
                            results[0]= result;
                            project.notifyAll();
                        }
                    }
                };
    			synchronized(project) {
                    engine.evaluate(stringValue, frame, listener, DebugEvent.EVALUATION_IMPLICIT, false);
    				try {
    					project.wait();
    				} catch (InterruptedException e) {
    					if (results[0] == null)
    						throw new RuntimeException(e);
    				}
    			}
    			IEvaluationResult result= results[0];
    			if (result == null)
    			    return null;
    			if (result.hasErrors()) {
    				String msg = "The following errors were encountered during evaluation.\n\n" + getErrors(result);
    				showError("Evaluation error", msg, null);
    				throw new EvaluationManager.EvaluationError(msg);
    			}
    			return result.getValue();
            }
        }
        return null;
    }
    
    public static String getUnqualifiedName(String name) {
    	int lastDot = name.lastIndexOf('.');
    	if (lastDot != -1)
    		name = name.substring(lastDot + 1);
    	return name;
    }
    
    /**
     * Extracts the errors messages out of a failing IEvaluationResult.
     * Copied from JavaObjectValueEditor.evaluate.
     * @param result A failing evaluation result.
     * @return A string representing the failure.
     */
    public static String getErrors(IEvaluationResult result) {
	    StringBuffer buffer = new StringBuffer();
	    //buffer.append("Error on evaluation of: ").append(result.getSnippet()).append("\n");
	    if (result.getException() == null) {
		    String[] messages = result.getErrorMessages();
		    for (int i = 0; i < messages.length; i++)
                buffer.append(messages[i]).append("\n "); //$NON-NLS-1$
	    } else {
	    	buffer.append(EvaluateAction.getExceptionMessage(result.getException()));
	    }
    	return buffer.toString();
    }

}
