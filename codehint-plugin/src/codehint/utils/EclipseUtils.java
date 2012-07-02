package codehint.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.sun.jdi.InvocationException;
import com.sun.jdi.ObjectReference;

import codehint.Activator;
import codehint.expreval.EvaluationManager;

public class EclipseUtils {
    
	/**
	 * Return the project associated with the given stack frame.
	 * (copied from JavaWatchExpressionDelegate)
	 * (copied from JavaObjectValueEditor)
	 * @param javaStackFrame The stack frame
	 * @return the project associate with the given stack frame.
	 */
	public static IJavaProject getProject(IJavaStackFrame javaStackFrame) {
		Object sourceElement = javaStackFrame.getLaunch().getSourceLocator().getSourceElement(javaStackFrame);
		if (!(sourceElement instanceof IJavaElement) && sourceElement instanceof IAdaptable)
			sourceElement = ((IAdaptable)sourceElement).getAdapter(IJavaElement.class);
		assert sourceElement instanceof IJavaElement;
		return ((IJavaElement)sourceElement).getJavaProject();
	}
    
    public static String getSignature(IVariable variable) {
    	try {
	        IJavaVariable javaVariable = (IJavaVariable)variable.getAdapter(IJavaVariable.class);
	        return javaVariable.getSignature();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
    public static boolean isObject(IVariable variable) {
    	return isObject(getSignature(variable));
    }
    
    public static boolean isObject(IJavaType type) throws DebugException {
    	return type == null || isObject(type.getSignature());
    }
    
    private static boolean isObject(String signature) {
    	return Signature.getTypeSignatureKind(signature) == Signature.CLASS_TYPE_SIGNATURE;
    }
    
    public static boolean isPrimitive(IVariable variable) {
    	return isPrimitive(getSignature(variable));
    }
    
    public static boolean isPrimitive(IJavaType type) throws DebugException {
    	return type != null && isPrimitive(type.getSignature());
    }
    
    private static boolean isPrimitive(String signature) {
    	return Signature.getTypeSignatureKind(signature) == Signature.BASE_TYPE_SIGNATURE;
    }
    
    public static boolean isArray(IVariable variable) {
    	return Signature.getTypeSignatureKind(getSignature(variable)) == Signature.ARRAY_TYPE_SIGNATURE;
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
     * a legal Java expression, except in the case of arrays,
     * where we show a debug-like view of them.
     * @param value The value whose string representation is desired.
     * @return A string that is the legal Java expression of
     * the given value, except for arrays, which show their values.
     * @throws DebugException if we cannot get the value.
     */
    public static String javaStringOfValue(IJavaValue value) throws DebugException {
    	if (value instanceof IJavaArray) {
    		StringBuilder sb = new StringBuilder();
    		sb.append("[");
    		for (IJavaValue arrValue: ((IJavaArray)value).getValues()) {
    			if (sb.length() > 1)
    				sb.append(",");
    			sb.append(javaStringOfValue(arrValue));
    		}
    		sb.append("]");
    		return sb.toString();
    	} else if ("C".equals(value.getSignature()))
    		return "'" + value.getValueString() + "'";
    	else if ("Ljava/lang/String;".equals(value.getSignature()))
    		return "\"" + value.getValueString() + "\"";
    	else
    		return value.getValueString();
    }
    
    public static IAstEvaluationEngine getASTEvaluationEngine(IJavaStackFrame stackFrame) {
    	return org.eclipse.jdt.debug.eval.EvaluationManager.newAstEvaluationEngine(getProject(stackFrame), (IJavaDebugTarget)stackFrame.getDebugTarget());
    }
    
    /**
     * Gets a unique set of compile errors from the given compiled expression.
     * @param compiled The compiled expression.
     * @return the unique set of compile errors from the given compiled expression.
     */
    public static String getCompileErrors(ICompiledExpression compiled) {
    	if (compiled.hasErrors()) {
    		return getSetOfCompileErrors(compiled).toString();
    	} else
    		return null;
    }
    
    /**
     * Gets a unique set of compile errors from the given compiled expression.
     * @param compiled The compiled expression.
     * @return the unique set of compile errors from the given compiled expression.
     */
    private static Set<String> getSetOfCompileErrors(ICompiledExpression compiled) {
    	if (compiled.hasErrors()) {
    		Set<String> errors = new HashSet<String>();  // Remove duplicate compile errors.
    		for (String error : compiled.getErrorMessages())
    			errors.add(error);
    		return errors;
    	} else
    		return null;
    }

    /**
     * Checks the given text for compile errors, including that it
     * has the correct type.
     * @param newText A string of the expression to compile.
     * @param typeName A string of the name of the desired type of the expression.
     * @param stackFrame The stack frame.
     * @param evaluationEngine The evaluation engine.
     * @return Any compile errors from the expression, including an error
     *  if it does not have the proper type.
     */
	public static String getCompileErrors(String newText, String typeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
		Set<String> errors = getSetOfCompileErrors(newText, typeName, stackFrame, evaluationEngine);
		if (errors == null)
			return null;
		else
			return errors.toString();
	}

    /**
     * Checks the given text for compile errors, including that it
     * has the correct type.
     * @param newText A string of the expression to compile.
     * @param typeName A string of the name of the desired type of the expression.
     * @param stackFrame The stack frame.
     * @param evaluationEngine The evaluation engine.
     * @return Any compile errors from the expression, including an error
     *  if it does not have the proper type.
     */
	public static Set<String> getSetOfCompileErrors(String newText, String typeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
		try {
			String exprStr = typeName == null ? newText : typeName + " _$_$" + " = " + newText + ";";
			ICompiledExpression compiled = evaluationEngine.getCompiledExpression(exprStr, stackFrame);
        	if (compiled.hasErrors())
        		return getSetOfCompileErrors(compiled);
        	return null;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
    
    public static IType getThisType(IJavaProject project, IJavaStackFrame stackFrame) throws DebugException, JavaModelException {
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
    
    public static String getValidTypeError(IJavaProject project, String varTypeName, IType thisType, String newTypeName) {
    	try {
    		if (thisType == null)
    			return null;
    		if (varTypeName.equals(newTypeName))  // ITypes dislike primitive types, so this handles them.
    			return null;
    		int firstArrayVar = varTypeName.indexOf("[]");
    		if (firstArrayVar != -1) {
        		int firstArrayNew = newTypeName.indexOf("[]");
    			if (firstArrayNew == -1 || varTypeName.length() - firstArrayVar != newTypeName.length() - firstArrayNew) {
    				int count = (varTypeName.length() - firstArrayVar) / 2;
    				return "Please enter a type with " + count + " array " + Utils.plural("dimension", "s", count) + ".";
    			} else  // Recursively check the component types since there seem to be no ITypes for arrays....
    				return getValidTypeError(project, varTypeName.substring(0, firstArrayVar), thisType, newTypeName.substring(0, firstArrayNew));
    		}
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
        	if (curType.getFullyQualifiedName('.').equals(varTypeName))  // The supertype hierarchy doesn't contain the type itself....
        		return null;
        	for (IType supertype: curType.newSupertypeHierarchy(null).getAllSupertypes(curType))
        		if (supertype.getFullyQualifiedName('.').equals(varTypeName))
        			return null;
			return "Please enter a subtype of " + varTypeName + ".";
    	} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
    }
    
    /**
     * Gets the type with the given unique name
     * @param typeName The name of the type to get, which does not need
     * to be fully-qualified but must be unique.
     * @param stackFrame The current stack frame.
     * @param target The debug target.
     * @return The IJavaType representing the given name.
     */
    public static IJavaType getType(String typeName, IJavaStackFrame stackFrame, IJavaDebugTarget target) {
		try {
			IJavaProject project = getProject(stackFrame);
			return getType(project, getThisType(project, stackFrame), typeName, target);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
    private static IJavaType getType(IJavaProject project, IType thisType, String typeName, IJavaDebugTarget target) {
    	try {
    		if (thisType == null || project == null || target == null)
    			return null;
    		// If this is an array type, first get the base type and then generate the array at the end. 
    		int firstArray = typeName.indexOf("[]");
    		String componentTypeName = firstArray == -1 ? typeName : typeName.substring(0, firstArray);
    		String arrayTypes = firstArray == -1 ? "" : typeName.substring(firstArray);
    		// Handle primitives
    		if ("int".equals(componentTypeName) || "boolean".equals(componentTypeName) || "long".equals(componentTypeName) || "byte".equals(componentTypeName) || "char".equals(componentTypeName) || "short".equals(componentTypeName) || "float".equals(componentTypeName) || "double".equals(componentTypeName))
    			return getFullyQualifiedType(typeName, target);
    		// Find the full type name.
    		String[][] allTypes = thisType.resolveType(componentTypeName);
    		assert allTypes != null && allTypes.length == 1 : typeName;
    		String[] typeParts = allTypes[0];
			String fullTypeName = "";
			typeParts[typeParts.length - 1] = typeParts[typeParts.length - 1].replace('.', '$');  // For some reason the IJavaDebugTarget.getJavaTypes method needs to have inner typesnames use '$' and not '.'.
			for (int i = 0; i < typeParts.length; i++) {
				if (fullTypeName.length() > 0)
					fullTypeName += ".";
				fullTypeName += typeParts[i];
			}
			return getFullyQualifiedType(fullTypeName + arrayTypes, target);
    	} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
    }
    
    /**
     * Gets the type with the given fully-qualified name.
     * @param fullTypeName The fully-qualified name of a type.
     * @param target The debug target.
     * @return The type with the given name.
     */
    public static IJavaType getFullyQualifiedType(String fullTypeName, IJavaDebugTarget target) {
		IJavaType type = getFullyQualifiedTypeIfExists(fullTypeName, target);
		assert type != null : fullTypeName;
		return type;
    }

    /**
     * Gets the type with the given fully-qualified name if one exists.
     * @param fullTypeName The fully-qualified name of a type.
     * @param target The debug target.
     * @return The type with the given name, or null.
     */
    public static IJavaType getFullyQualifiedTypeIfExists(String fullTypeName, IJavaDebugTarget target) {
    	try {
			IJavaType[] curTypes = target.getJavaTypes(fullTypeName);
			if (curTypes == null || curTypes.length != 1)
				return null;
			else
				return curTypes[0];
    	} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
   	public static void insertIndentedLineAtCurrentDebugPoint(String text, int line) throws BadLocationException {
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
   	
   	public static void replaceLineAtCurrentDebugPoint(String newText, int line) throws BadLocationException {
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
   		final IStatus status = new Status(severity, Activator.PLUGIN_ID, IStatus.OK, text, exception);
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				ErrorDialog.openError(getShell(), title, null, status);
			}
		});
   	}
   	
   	public static void log(String msg) {
   		Activator.getDefault().getLog().log(new Status(IStatus.INFO, Activator.PLUGIN_ID, msg));
   	}

    /**
     * Evaluates the given snippet. Reports any errors to the user.
     * @param stringValue the snippet to evaluate
     * @param stack The current stack frame.
     * @return the value that was computed or <code>null</code> if any errors occurred.
     * @throws DebugException 
     */
   	// TODO: Integrate with codehint.expreval code?
    public static IJavaValue evaluate(String stringValue, final IJavaStackFrame stack) throws DebugException {
        IAstEvaluationEngine engine = getASTEvaluationEngine(stack);
        final IEvaluationResult[] results = new IEvaluationResult[1];
        IEvaluationListener listener = new IEvaluationListener() {
            @Override
			public void evaluationComplete(IEvaluationResult result) {
                synchronized (stack) {
                    results[0] = result;
                    stack.notifyAll();
                }
            }
        };
		synchronized(stack) {
            engine.evaluate(stringValue, stack, listener, DebugEvent.EVALUATION_IMPLICIT, false);
			try {
				stack.wait();
			} catch (InterruptedException e) {
				if (results[0] == null)
					throw new RuntimeException(e);
			}
		}
		IEvaluationResult result = results[0];
		if (result == null)
		    return null;
		if (result.hasErrors()) {
			String msg = "The following errors were encountered during evaluation.\n\n" + getErrors(result);
			showError("Evaluation error", msg, null);
			throw new EvaluationManager.EvaluationError(msg);
		}
		return result.getValue();
    }
    
    public static String getUnqualifiedName(String name) {
    	int lastDot = name.lastIndexOf('.');
    	if (lastDot != -1)
    		name = name.substring(lastDot + 1);
    	return name;
    }
    
    public static IJavaType getTypeAndLoadIfNeededAndExists(String typeName, IJavaStackFrame stack, IJavaDebugTarget target) {
    	try {
    		IJavaType type = getFullyQualifiedTypeIfExists(typeName, target);
    		if (type != null)
    			return type;
	    	if (getASTEvaluationEngine(stack).getCompiledExpression(getClassLoadExpression(typeName), stack).hasErrors())
	    		return null;
	    	else
	    		return loadAndGetType(typeName, stack, target);
    	} catch (DebugException e) {
    		throw new RuntimeException(e);
    	}
    }
    
    public static IJavaType getTypeAndLoadIfNeeded(String typeName, IJavaStackFrame stack, IJavaDebugTarget target) {
		IJavaType type = getFullyQualifiedTypeIfExists(typeName, target);
		if (type == null) {
			type = loadAndGetType(typeName, stack, target);
			/*if (type == null)
				System.out.println("Failed to load " + typeName);
			else
				System.out.println("Loaded " + typeName);*/
		}
		return type;
    }
    
    private static IJavaType loadAndGetType(String typeName, IJavaStackFrame stack, IJavaDebugTarget target) {
		loadClass(typeName, stack);
		IJavaType type = getFullyQualifiedTypeIfExists(typeName, target);
		if (type != null)  // getType will fail for inner types but getFullyQualified will work if they use $, so we try it first.
			return type;
		else
			return getType(typeName, stack, target);
    }
    
    // TODO: There must be a better way to do this.
    private static void loadClass(String typeName, IJavaStackFrame stack) {
    	try {
    		int dollar = typeName.indexOf('$');  // For inner classes, we seem to need to load the outer class first.
    		if (dollar != -1)
        		evaluate(getClassLoadExpression(typeName.substring(0, dollar)), stack);
    		evaluate(getClassLoadExpression(typeName), stack);
    	} catch (DebugException e) {
    		throw new RuntimeException(e);
    	}
    }
    
    private static String getClassLoadExpression(String typeName) {
    	return sanitizeTypename(typeName) + ".class";
    }
    
    /**
     * Checks whether the given classname represents an anonymous class
     * (and therefore cannot be instantiated as-is).  Such names end
     * with a dollar sign followed by a sequence number.
     * @param name The name of the class.
     * @return Whether the given name represents an anonymous class.
     */
    /*public static boolean isAnonymousClass(String name) {
    	int lastDollar = name.lastIndexOf('$');
    	if (lastDollar == -1)
    		return false;
    	for (int i = lastDollar + 1; i < name.length(); i++) {
    		char ch = name.charAt(i);
    		if (ch < '0' || ch > '9')
    			return false;
    	}
    	return true;
    }*/
    
    /**
     * Extracts the errors messages out of a failing IEvaluationResult.
     * Copied from JavaObjectValueEditor.evaluate.
     * @param result A failing evaluation result.
     * @return A string representing the failure.
     */
    private static String getErrors(IEvaluationResult result) {
	    StringBuilder sb = new StringBuilder();
	    //buffer.append("Error on evaluation of: ").append(result.getSnippet()).append("\n");
	    if (result.getException() == null) {
		    String[] messages = result.getErrorMessages();
		    for (int i = 0; i < messages.length; i++)
		    	sb.append(messages[i]).append("\n ");
	    } else
	    	sb.append(getExceptionMessage(result.getException()));
    	return sb.toString();
    }
	
    /*
     * Inspired by org.eclipse.jdt.internal.debug.ui.actions.EvaluateAction.getExceptionMessage.
     */
	private static String getExceptionMessage(Throwable exception) {
		if (exception instanceof CoreException) {
			CoreException ce = (CoreException)exception;
			Throwable throwable= ce.getStatus().getException();
			if (throwable instanceof InvocationException) {
				ObjectReference ref = ((InvocationException)exception).exception();
				return "An exception occurred: " + ref.referenceType().name();
			} else if (throwable instanceof CoreException)
				return getExceptionMessage(throwable);
			return ce.getStatus().getMessage();
		}
		String message = "An exception occurred: " + exception.getClass(); 
		if (exception.getMessage() != null)
			message += " - " + exception.getMessage();
		return message;
	}

}
