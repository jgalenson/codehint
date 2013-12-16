package codehint.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.sun.jdi.Field;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;

import codehint.Activator;
import codehint.expreval.EvaluationManager;
import codehint.expreval.TimeoutChecker;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.exprgen.TypeCache;

public final class EclipseUtils {
    
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
    
	/**
	 * Gets the Java signature of the given variable.
	 * @param variable The variable.
	 * @return The signature of the given variable.
	 */
    public static String getSignature(IVariable variable) {
    	try {
	        IJavaVariable javaVariable = (IJavaVariable)variable.getAdapter(IJavaVariable.class);
	        return javaVariable.getSignature();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
    /**
     * Checks whether the given variable is an object.
     * @param variable The variable to check.
     * @return Whether the given variable is an object.
     */
    public static boolean isObject(IVariable variable) {
    	return isObject(getSignature(variable));
    }

    /**
     * Checks whether the given type is an object.
     * @param type The type to check.
     * @return Whether the given type is an object.
     */
    public static boolean isObject(IJavaType type) {
    	try {
	    	return type == null || isObject(type.getSignature());
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Checks whether the given signature represents an object.
     * @param signature The signature to check.
     * @return Whether the given signature represents an object.
     */
    private static boolean isObject(String signature) {
    	return Signature.getTypeSignatureKind(signature) == Signature.CLASS_TYPE_SIGNATURE;
    }

    /**
     * Checks whether the given variable is a primitive.
     * @param variable The variable to check.
     * @return Whether the given variable is a primitive.
     */
    public static boolean isPrimitive(IVariable variable) {
    	return isPrimitive(getSignature(variable));
    }

    /**
     * Checks whether the given type is a primitive.
     * @param type The type to check.
     * @return Whether the given type is a primitive.
     */
    public static boolean isPrimitive(IJavaType type) {
    	try {
    		return type != null && isPrimitive(type.getSignature());
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Checks whether the given signature represents a primitive.
     * @param signature The signature to check.
     * @return Whether the given signature represents a primitive.
     */
    public static boolean isPrimitive(String signature) {
    	return Signature.getTypeSignatureKind(signature) == Signature.BASE_TYPE_SIGNATURE;
    }

    /**
     * Checks whether the given variable is an array.
     * @param variable The variable to check.
     * @return Whether the given variable is an array.
     */
    public static boolean isArray(IVariable variable) {
    	return isArray(getSignature(variable));
    }

    /**
     * Checks whether the given type is an array.
     * @param type The type to check.
     * @return Whether the given type is an array.
     */
    public static boolean isArray(IJavaType type) {
    	try {
			return isArray(type.getSignature());
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Checks whether the given signature represents an array.
     * @param signature The signature to check.
     * @return Whether the given signature represents an array.
     */
    private static boolean isArray(String signature) {
    	return Signature.getTypeSignatureKind(signature) == Signature.ARRAY_TYPE_SIGNATURE;
    }

    /**
     * Checks whether the given type is an integer.
     * @param type The type to check.
     * @return Whether the given type is an integer.
     */
	public static boolean isInt(IJavaType type) throws DebugException {
		return type != null && "I".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a boolean.
     * @param type The type to check.
     * @return Whether the given type is a boolean.
     */
	public static boolean isBoolean(IJavaType type) throws DebugException {
		return type != null && "Z".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a long.
     * @param type The type to check.
     * @return Whether the given type is a long.
     */
	public static boolean isLong(IJavaType type) throws DebugException {
		return type != null && "J".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a byte.
     * @param type The type to check.
     * @return Whether the given type is a byte.
     */
	public static boolean isByte(IJavaType type) throws DebugException {
		return type != null && "B".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a char.
     * @param type The type to check.
     * @return Whether the given type is a char.
     */
	public static boolean isChar(IJavaType type) throws DebugException {
		return type != null && "C".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a short.
     * @param type The type to check.
     * @return Whether the given type is a short.
     */
	public static boolean isShort(IJavaType type) throws DebugException {
		return type != null && "S".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a float.
     * @param type The type to check.
     * @return Whether the given type is a float.
     */
	public static boolean isFloat(IJavaType type) throws DebugException {
		return type != null && "F".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is a double.
     * @param type The type to check.
     * @return Whether the given type is a double.
     */
	public static boolean isDouble(IJavaType type) throws DebugException {
		return type != null && "D".equals(type.getSignature());
	}

    /**
     * Checks whether the given type is an object or interface.
     * @param type The type to check.
     * @return Whether the given type is an object or interface.
     */
	public static boolean isObjectOrInterface(IJavaType type) {
		return type instanceof IJavaClassType || type instanceof IJavaInterfaceType;
	}
   	
	/**
	 * Rewrites a type name so that it can be executed in code.
	 * For example "Test.Foo foo" compiles but "Test$Foo foo",
	 * the full typename, does not.
	 * TODO: There must be a better way to do this through Eclipse.
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

    /**
     * Gets the current editor part.
     * @return The current editor part.
     */
    private static IEditorPart getActiveEditorPart() {
   		IWorkbench workbench = PlatformUI.getWorkbench();
       	IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
       	return page.getActiveEditor();
    }
    
    /**
     * Gets the current text editor.
     * @return The current text editor.
     */
    public static ITextEditor getActiveTextEditor() {
    	IEditorPart editorPart = getActiveEditorPart();
    	if (editorPart == null)
    		return null;
    	ITextEditor editor = (ITextEditor) editorPart.getAdapter(ITextEditor.class);
    	assert editor != null;
    	return editor;
    }

	/**
	 * Gets the file being edited by the given text editor.
	 * @param editor The text editor.
	 * @return The file being edited by the text editor.
	 */
	public static IFile getEditorFile(ITextEditor editor) {
		return ((IFileEditorInput)((IEditorPart)editor.getAdapter(IEditorPart.class)).getEditorInput()).getFile();
	}
    
    /**
     * Gets the working location of the CodeHint plugin.
     * @param project The Java project.
     * @return The working location of the plugin.
     */
    public static String getPluginWorkingLocation(IJavaProject project) {
    	return project.getProject().getWorkingLocation(Activator.PLUGIN_ID).toOSString();
    }
    
    /**
     * Extracts the codehint-library.jar file from the plugin's
     * jar file into the plugin's working directory, if it is not
     * already there.
     * @param stack The stack frame.
     */
    private static File extractLibrary(IJavaStackFrame stack) {
		IJavaProject project = getProject(stack);
		String fileSep = System.getProperty("file.separator");
		File outFile = new File(getPluginWorkingLocation(project) + fileSep + "codehint-lib.jar");
    	if (!outFile.exists()) {
        	try {
	    		InputStream is = getFileFromBundle("lib" + fileSep + "codehint-lib.jar");
		    	FileOutputStream os = new FileOutputStream(outFile);
		    	int c;
		    	while ((c = is.read()) != -1)
		    		os.write(c);
		    	is.close();
		    	os.close();
    		} catch (IOException e) {
    			outFile.delete();
    			throw new RuntimeException(e);
    		}
    	}
    	return outFile;
    }

    /**
     * Gets an input stream to a file stored in the plugin's
     * jar file.
     * @param filename The path to the file in the jar.
     * @return An InputStream containing the data of the
     * given filename.
     * @throws IOException
     */
	public static InputStream getFileFromBundle(String filename) throws IOException {
		return FileLocator.openStream(Platform.getBundle(Activator.PLUGIN_ID), new Path(filename), false);
	}
    
    /**
     * Loads the class with the given name from the library jar file
     * in the plugin.
     * @param className The name of the class to load from the library.
     * @param stack The stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The IJavaClassType of the given class.
     */
    public static IJavaClassType loadLibrary(String className, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
    	try {
    		IJavaClassType type = (IJavaClassType)getFullyQualifiedTypeIfExistsUnchecked(className, target, typeCache);
	    	if (type != null)
	    		return type;
	    	if (getASTEvaluationEngine(stack).getCompiledExpression(className + ".class", stack).hasErrors()) {
		    	File libFile = extractLibrary(stack);
		    	String evalStr = "Class.forName(\"" + className + "\", true, java.net.URLClassLoader.newInstance(new java.net.URL[] { new java.net.URL(\"file://" + libFile.getAbsolutePath() + "\") }))";
	    		EclipseUtils.evaluate(evalStr, stack);
		    	return (IJavaClassType)getFullyQualifiedTypeIfExistsUnchecked(className, target, typeCache);
	    	} else
	    		return (IJavaClassType)loadAndGetType(className, stack, target, typeCache);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
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
     * Parses the given string into a Java expression.
     * @param parser The AST parser to use.
     * @param str The string of a Java expression.
     * @param project The project.
     * @param unitName The unit name.
     * @return The resulting AST.
     */
    /*public static ASTNode parseClass(ASTParser parser, String str, IJavaProject project, String unitName) {
    	// We apparently have to manually tell it to use Java 1.5....
		Map<?, ?> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_5, options);
		parser.setCompilerOptions(options);
		// We want to parse a whole class.
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		// Get type information.
		parser.setResolveBindings(true);
		parser.setProject(project);
		parser.setUnitName(unitName);
		// Do the actual parsing.
		parser.setSource(str.toCharArray());
		return parser.createAST(null);
    }*/
    
    /**
     * Returns a string representing the given value this is
     * a legal Java expression, except in the case of arrays,
     * where we show a debug-like view of them.
     * @param value The value whose string representation is desired.
     * @param stack The current stack frame.
     * @param callToString Whether we should call toString on
     * objects or simply use their default strings in Eclipse.
     * @return A string that is the legal Java expression of
     * the given value, except for arrays, which show their values.
     * @throws DebugException if we cannot get the value.
     */
    public static String javaStringOfValue(IJavaValue value, IJavaStackFrame stack, boolean callToString) throws DebugException {
    	String sig = value.getSignature();
		if (value.isNull())
    		return "null";
    	else if (value instanceof IJavaArray) {
    		StringBuilder sb = new StringBuilder();
    		sb.append("[");
    		for (IJavaValue arrValue: ((IJavaArray)value).getValues()) {
    			if (sb.length() > 1)
    				sb.append(",");
    			sb.append(javaStringOfValue(arrValue, stack, callToString));
    		}
    		sb.append("]");
    		return sb.toString();
    	} else if ("C".equals(sig))
    		return "'" + value.getValueString() + "'";
    	else if ("Ljava/lang/String;".equals(sig))
    		return "\"" + value.getValueString() + "\"";
    	else if (callToString && value instanceof IJavaObject) {
    		try {
    			return ((IJavaObject)value).sendMessage("toString", "()Ljava/lang/String;", new IJavaValue[] { }, (IJavaThread)stack.getThread(), null).getValueString();
    		} catch (DebugException e) {
    			return value.toString();
    		}
    	}
    	String str = value.toString();  // For Objects, getValueString() returns just the id and not the type.
    	if ("NaN".equals(str) && "F".equals(sig))
    		return "Float.NaN";
    	else if ("Infinity".equals(str) && "F".equals(sig))
    		return "Float.POSITIVE_INFINITY";
    	else if ("-Infinity".equals(str) && "F".equals(sig))
    		return "Float.NEGATIVE_INFINITY";
    	else if ("NaN".equals(str) && "D".equals(sig))
    		return "Double.NaN";
    	else if ("Infinity".equals(str) && "D".equals(sig))
    		return "Double.POSITIVE_INFINITY";
    	else if ("-Infinity".equals(str) && "D".equals(sig))
    		return "Double.NEGATIVE_INFINITY";
    	else if ("F".equals(sig))
    		return str + "f";
    	else if ("D".equals(sig))
    		return str + "d";
    	else if ("J".equals(sig))
    		return str + "L";
    	return str;
    }

	/**
	 * Gets the name of the given type.
	 * @param type The type.
	 * @return The name of the type, or "null" if it is null.
	 * @throws DebugException
	 */
	public static String getTypeName(IJavaType type) throws DebugException {
		return type == null ? "null" : type.getName();
	}
    
    /**
     * Gets an AST evaluation engine.
     * @param stackFrame The current stack frame.
     * @return An AST evaluation engine.
     */
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
     * has the correct type, if applicable.
     * @param newText A string of the expression to compile.
     * @param typeName A string of the name of the desired type of
     * the expression or null if we should just evaluate the expression.
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
     * has the correct type, if applicable.
     * @param newText A string of the expression to compile.
     * @param typeName A string of the name of the desired type of
     * the expression or null if we should just evaluate the expression.
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
    
	/**
	 * Gets the IType representation of the this object.
	 * @param project The current project.
	 * @param stackFrame The current stack frame.
	 * @return An IType representation of the this object.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
    public static IType getThisType(IJavaProject project, IJavaStackFrame stackFrame) throws DebugException, JavaModelException {
    	String thisTypeName = stackFrame.getDeclaringTypeName();
		IType thisType = project.findType(thisTypeName);
		if (thisType != null && !thisTypeName.contains("$"))  // Do not use anonymous classes (e.g., Foo$1).
			return thisType;
		// The above will fail for anonymous classes (e.g., Foo$1; it gets an itype but it gets weird results later on), so we just get the outer part before the $.
		if (thisTypeName.contains("$")) {
			String outerTypeName = thisTypeName.substring(0, thisTypeName.indexOf('$'));
			return project.findType(outerTypeName);
		}
		return null;
    }
    
    /**
     * Gets the string representation of an error when trying
     * to use the given string as a subtype of the variable's type.
     * @param project The current project.
     * @param varTypeName The name of the type of the variable,
     * or null if it can be any type.
     * @param thisType The type of the this object.
     * @param newTypeName A string that should be the name of a
     * subtype of the variable type name.
     * @return An error for why the string is not acceptable or
     * null if it is a valid subtype of the variable type.
     */
    public static String getValidTypeError(IJavaProject project, String varTypeName, IType thisType, String newTypeName) {
    	try {
    		if (thisType == null)
    			return null;
    		if (newTypeName.contains("<"))
    			return "Please enter an unparameterized type";
			int firstArrayNew = newTypeName.indexOf("[]");
    		int firstArrayVar = varTypeName == null ? -1 : varTypeName.indexOf("[]");
			if (varTypeName != null) {
	    		int numArrDimsVar = firstArrayVar == -1 ? 0 : (varTypeName.length() - firstArrayVar) / 2;
	    		int numArrDimsNew = firstArrayNew == -1 ? 0 : (newTypeName.length() - firstArrayNew) / 2;
				if (numArrDimsVar != numArrDimsNew)
					return "Please enter a type with " + numArrDimsVar + " array " + Utils.plural("dimension", "s", numArrDimsVar) + ".";
			}
    		if (firstArrayNew != -1)  // Recursively check the component types since there seem to be no ITypes for arrays....
				return getValidTypeError(project, varTypeName == null ? varTypeName : varTypeName.substring(0, firstArrayVar), thisType, newTypeName.substring(0, firstArrayNew));
    		if (newTypeName.equals("byte") || newTypeName.equals("short") || newTypeName.equals("int") || newTypeName.equals("long") || newTypeName.equals("float") || newTypeName.equals("double") || newTypeName.equals("char") || newTypeName.equals("boolean"))  // ITypes dislike primitive types, so this handles them.
    			return null;
			String[][] allTypes;
    		try {
    			allTypes = thisType.resolveType(newTypeName);
    		} catch (IllegalArgumentException ex) {
    			return "Please enter a valid type.";
    		}
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
    		if (varTypeName != null) {
	        	IType curType = project.findType(fullTypeName);
	        	if (curType.getFullyQualifiedName('.').equals(varTypeName))  // The supertype hierarchy doesn't contain the type itself....
	        		return null;
	        	for (IType supertype: curType.newSupertypeHierarchy(null).getAllSupertypes(curType))
	        		if (supertype.getFullyQualifiedName('.').equals(varTypeName))
	        			return null;
				return "Please enter a subtype of " + varTypeName + ".";
    		} else
    			return null;
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
     * @param typeCache The type cache.
     * @return The IJavaType representing the given name.
     */
    public static IJavaType getType(String typeName, IJavaStackFrame stackFrame, IJavaDebugTarget target, TypeCache typeCache) {
		try {
    		IJavaType cachedType = typeCache.get(typeName);
    		if (cachedType != null)
    			return cachedType;
			IJavaProject project = getProject(stackFrame);
			IJavaType type = getType(project, getThisType(project, stackFrame), typeName, stackFrame, target, typeCache);
			typeCache.add(typeName, type);
			return type;
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Gets the type with the given unique name
     * @param project The current project.
     * @param thisType The type of the this object.
     * @param typeName The name of the type to get, which does not need
     * to be fully-qualified but must be unique.
     * @param stack The stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The IJavaType representing the given name.
     */
    private static IJavaType getType(IJavaProject project, IType thisType, String typeName, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
    	if (thisType == null || project == null || target == null)
			return null;
		return getFullyQualifiedType(getFullyQualifiedTypeName(thisType, typeName), stack, target, typeCache);
    }

    /**
     * Gets the fully-qualified name of the given type name.
     * @param thisType The type of the this object.
     * @param typeName The name of the type to get, which does not need
     * to be fully-qualified but must be unique.
     * @return The fully-qualified name of the given type name.
     */
    public static String getFullyQualifiedTypeName(IType thisType, String typeName) {
    	try {
    		// If this is an array type, first get the base type and then generate the array at the end.
    		int firstArray = typeName.indexOf("[]");
    		String componentTypeName = firstArray == -1 ? typeName : typeName.substring(0, firstArray);
    		String arrayTypes = firstArray == -1 ? "" : typeName.substring(firstArray);
    		// Handle primitives
    		if ("int".equals(componentTypeName) || "boolean".equals(componentTypeName) || "long".equals(componentTypeName) || "byte".equals(componentTypeName) || "char".equals(componentTypeName) || "short".equals(componentTypeName) || "float".equals(componentTypeName) || "double".equals(componentTypeName))
    			return typeName;
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
			return fullTypeName + arrayTypes;
    	} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
    }
    
    /**
     * Gets the type with the given fully-qualified name.
     * @param fullTypeName The fully-qualified name of a type.
     * @param stack The stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The type with the given name.
     */
    public static IJavaType getFullyQualifiedType(String fullTypeName, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		IJavaType type = getFullyQualifiedTypeIfExists(fullTypeName, stack, target, typeCache);
		assert type != null : fullTypeName;
		return type;
    }

    /**
     * Gets the type with the given fully-qualified name if one exists.
     * @param fullTypeName The fully-qualified name of a type.
     * @param stack The stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The type with the given name, or null.
     */
    public static IJavaType getFullyQualifiedTypeIfExists(String fullTypeName, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
    	try {
    		IJavaType cachedType = typeCache.get(fullTypeName);
    		if (cachedType != null)
    			return cachedType;
    		if (typeCache.isIllegal(fullTypeName))
    			return null;
			if (isIllegalType(fullTypeName, stack, typeCache)) {
    			typeCache.markIllegal(fullTypeName);
    			return null;
			}
			return getFullyQualifiedTypeIfExistsUnchecked(fullTypeName, target, typeCache);
    	} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Gets the type with the given fully-qualified name if one exists.
     * This version does not either read from or write to the type cache
     * and so should be used carefully to avoid slowdowns.
     * @param fullTypeName The fully-qualified name of a type.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The type with the given name, or null.
     */
	private static IJavaType getFullyQualifiedTypeIfExistsUnchecked(String fullTypeName, IJavaDebugTarget target, TypeCache typeCache) throws DebugException {
		IJavaType[] curTypes = target.getJavaTypes(fullTypeName);
		if (curTypes == null || curTypes.length != 1)
			return null;
		else {
			IJavaType curType = curTypes[0];
			typeCache.add(fullTypeName, curType);
			return curType;
		}
	}

    /**
     * Checks whether the type with the given is legal.
     * We need this because Eclipse sometimes gives us the IJavaType
     * for types that are not available on the classpath and hence
     * cannot be used.
     * Note that this can have false negatives; there are for some
     * reason types that compile but where evaluation of them
     * fails.
     * @param typeName The name of the type.
     * @param stack The stack frame.
     * @return Whether the given type can legally be used.
     * @throws DebugException
     */
	private static boolean isIllegalType(String typeName, IJavaStackFrame stack, TypeCache typeCache) throws DebugException {
		if (typeCache.isCheckedLegal(typeName))
			return false;
		return getASTEvaluationEngine(stack).getCompiledExpression(getClassLoadExpression(typeName), stack).hasErrors();
	}
	
	/**
	 * Gets the component type of an array or the type
	 * itself if it does not represent an array type.
	 * For example, given "java.lang.String[][]" ir
	 * returns "java.lang.String".
	 * @param type The name of the type.
	 * @return The component type of the given type if
	 * it is an array type and the type itself otherwise.
	 */
	public static String getComponentType(String type) {
		int firstArrayIndex = type.indexOf('[');
		if (firstArrayIndex == -1)
			return type;
		else
			return type.substring(0, firstArrayIndex);
	}
	
	/**
	 * Gets the text at the given line.
	 * @param document The document.
	 * @param line The line number.
	 * @return The text in the document at the given line.
	 * @throws BadLocationException
	 */
	public static String getTextAtLine(IDocument document, int line) throws BadLocationException {
    	//TODO: Expression could be spread across multiple lines
   		int offset = document.getLineOffset(line);
   		int length = document.getLineLength(line);
   		return document.get(offset, length);
	}
    
    /**
     * Inserts the given text at the given line.
     * @param text The text to insert.
     * @param line The line at which to insert it.
     * @throws BadLocationException
     */
   	public static void insertIndentedLine(String text, int line) throws BadLocationException {
   		IDocument document = getDocument();
   		
   		int offset = document.getLineOffset(line);
   		int firstchar = offset;
   		while (document.getChar(firstchar) == ' ' || document.getChar(firstchar) == '\t')
   			firstchar++;
   		String indent = document.get(offset, firstchar - offset);
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(firstchar, 0, text + "\n" + indent);
   	}
    
    /**
     * Inserts the given text after the given line.
     * @param text The text to insert.
     * @param line The line after which to insert it.
     * @throws BadLocationException
     */
   	public static void insertIndentedLineAfter(String text, int line) throws BadLocationException {
   		IDocument document = getDocument();
   		
   		int offset = document.getLineOffset(line);
   		int firstchar = offset;
   		while (document.getChar(firstchar) == ' ' || document.getChar(firstchar) == '\t')
   			firstchar++;
   		String indent = document.get(offset, firstchar - offset);
   		while (document.getChar(firstchar) != '\n')
   			firstchar++;
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(++firstchar, 0, indent + text + "\n");
   	}

    /**
     * Replaces the text at the given line with the next text.
     * @param newText The text to insert.
     * @param line The line at which to insert it.
     * @throws BadLocationException
     */
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
   	
   	/**
   	 * Deletes the next at the given line.
   	 * @param line The line at which to delete text.
   	 * @throws BadLocationException
   	 */
   	public static void deleteLine(int line) throws BadLocationException {
   		IDocument document = getDocument();
   		
   		int firstChar = document.getLineOffset(line);
   		int lastChar = firstChar;
   		while (document.getChar(lastChar) != '\n')
   			lastChar++;
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(firstChar, lastChar - firstChar + 1, "");
   	}
   	
   	/**
   	 * Gets the current document.
   	 * This must be called from the UI thread.
   	 * @return The current document.
   	 */
   	public static IDocument getDocument() {
   		return getDocument(EclipseUtils.getActiveTextEditor());
   	}

	/**
	 * Gets the document.
	 * @param editor The text editor.
	 * @return The document.
	 */
	public static IDocument getDocument(ITextEditor editor) {
		return editor.getDocumentProvider().getDocument(editor.getEditorInput());
	}

   	/**
   	 * Gets the current shell.
   	 * This must be called from the UI thread.
   	 * @return The current shell.
   	 */
	public static Shell getShell() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}
   	
	/**
	 * Shows an error to the user.
	 * @param title The title of the textbox.
	 * @param text The text of the error to show.
	 * @param exception The exception that caused the error, if any.
	 */
   	public static void showError(final String title, String text, Throwable exception) {
   		showErrorOrWarning(title, text, exception, IStatus.ERROR);
   	}

	/**
	 * Shows a warning to the user.
	 * @param title The title of the textbox.
	 * @param text The text of the warning to show.
	 * @param exception The exception that caused the warning, if any.
	 */
   	public static void showWarning(final String title, String text, Throwable exception) {
   		showErrorOrWarning(title, text, exception, IStatus.WARNING);
   	}

	/**
	 * Shows an error or warning to the user.
	 * @param title The title of the textbox.
	 * @param text The text of the error or warning to show.
	 * @param exception The exception that caused the error or warning, if any.
	 */
   	private static void showErrorOrWarning(final String title, String text, Throwable exception, int severity) {
   		final IStatus status = new Status(severity, Activator.PLUGIN_ID, IStatus.OK, text, exception);
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				ErrorDialog.openError(getShell(), title, null, status);
			}
		});
   	}
   	
   	/**
   	 * Shows a question to the user.
   	 * @param title The title of the dialog.
   	 * @param text The text of the question.
   	 * @return True if the user clicked yes and false otherwise.
   	 */
   	public static boolean showQuestion(final String title, final String text) {
   		final boolean[] result = new boolean[] { false }; 
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				result[0] = MessageDialog.openQuestion(getShell(), title, text);
			}
		});
		return result[0];
   	}
   	
   	/**
   	 * Logs the given message to Eclipse's log.
   	 * @param msg The message to log.
   	 */
   	public static void log(String msg) {
   		Activator.getDefault().getLog().log(new Status(IStatus.INFO, Activator.PLUGIN_ID, msg));
   	}

    /**
     * Evaluates the given snippet. Reports any errors to the user.
     * TODO: Integrate with codehint.expreval code?
     * @param stringValue the snippet to evaluate
     * @param stack The current stack frame.
     * @return the value that was computed or <code>null</code> if any errors occurred.
     * @throws DebugException 
     */
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
			if (stack.isTerminated())  // If the stack is terminated the wait below will hang forever, so abort in that case.
				return null;
            engine.evaluate(stringValue, stack, listener, DebugEvent.EVALUATION_IMPLICIT, false);
			try {
				stack.wait(TimeoutChecker.TIMEOUT_TIME_MS);  // Timeout the execution.
			} catch (InterruptedException e) {
				if (results[0] == null)
					throw new RuntimeException(e);
			}
		}
		IEvaluationResult result = results[0];
		if (result == null) {  // The evaluation timed out, so we need to cancel it and wait for it to finish.  If we don't wait, the thread will be in a bad state and error for future evaluations until it finishes.
			IJavaThread thread = (IJavaThread)stack.getThread();
			thread.terminateEvaluation();  // Unfortunately, we cannot easily tell when it actually terminates (this method just sets a flag asking it to).
			try {
				for (int i = 0; thread.isPerformingEvaluation(); i++) {
					if (i == 20)  // Eventually give up on the termination and abort.
						throw new EvaluationManager.EvaluationError("Unable to terminate evaluation.");
					Thread.sleep(TimeoutChecker.TIMEOUT_TIME_MS / 10);
				}
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		    return null;
		}
		if (result.hasErrors()) {
			String msg = "The following errors were encountered during evaluation.\n\n" + getErrors(result);
			//showError("Evaluation error", msg, null);
			throw new EvaluationManager.EvaluationError(msg);
		}
		return result.getValue();
    }
    
    /**
     * Gets the unqualified version of the given type.
     * For example, given "java.lang.String" it returns
     * "Strings".
     * @param name The name of a type.
     * @return An unqualified version of the name of the
     * given type.
     */
    public static String getUnqualifiedName(String name) {
    	int lastDot = name.lastIndexOf('.');
    	if (lastDot != -1)
    		name = name.substring(lastDot + 1);
    	return name;
    }

    /**
     * Gets a type if it exists, loading it if needed.
     * @param typeName The name of the type to get.
     * @param stack The current stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The type with the given name, or null if
     * no such type exists.
     */
    public static IJavaType getTypeAndLoadIfNeededAndExists(String typeName, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
    	try {
    		IJavaType type = getFullyQualifiedTypeIfExists(typeName, stack, target, typeCache);
    		if (type != null)
    			return type;
	    	if (isIllegalType(typeName, stack, typeCache))
	    		return null;
	    	else if (typeCache.isIllegal(typeName))
				return null;
	    	else
	    		return loadAndGetType(typeName, stack, target, typeCache);
    	} catch (DebugException e) {
    		throw new RuntimeException(e);
    	}
    }
    
    /**
     * Gets a type, loading it if needed.
     * @param typeName The name of the type to get.
     * @param stack The current stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The type with the given name.
     */
    public static IJavaType getTypeAndLoadIfNeeded(String typeName, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		IJavaType type = getFullyQualifiedTypeIfExists(typeName, stack, target, typeCache);
		if (type == null) {
			if (typeCache.isIllegal(typeName))
				return null;
			type = loadAndGetType(typeName, stack, target, typeCache);
			/*if (type == null)
				System.out.println("Failed to load " + typeName);
			else
				System.out.println("Loaded " + typeName);*/
		}
		if (typeName.endsWith("[]"))  // If an array's component type is not loaded, we can crash during evaluation of expressions involving it.
			getTypeAndLoadIfNeeded(typeName.substring(0, typeName.length() - 2), stack, target, typeCache);
		return type;
    }
    
    /**
     * Load and return the given type.
     * @param typeName The name of the type to load and return.
     * @param stack The current stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @return The newly-loaded type.
     */
    private static IJavaType loadAndGetType(String typeName, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
    	try {
    		loadClass(typeName, stack);
    	} catch (EvaluationError e) {  // In some cases types appear to be legal statically (they compile) but crash on evaluation, so we catch that here.
    		typeCache.markIllegal(typeName);
    		return null;
    	}
		IJavaType type = getFullyQualifiedTypeIfExists(typeName, stack, target, typeCache);
		if (type != null) {  // getType will fail for inner types but getFullyQualified will work if they use $, so we try it first.
			typeCache.add(typeName, type);
			return type;
		} else
			return getType(typeName, stack, target, typeCache);
    }
    
    /**
     * Loads the given type.
     * TODO: There must be a better way to do this.
     * @param typeName The name of the type to load.
     * @param stack The current stack frame.
     */
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
    
    /**
     * Gets a string whose evaluation will load the given type.
     * @param typeName The name of a type to load.
     * @return A string whose evaluation will load the given type.
     */
    private static String getClassLoadExpression(String typeName) {
    	return sanitizeTypename(typeName) + ".class";
    }
    
    /**
     * Tries to load the given types and returns whether or not
     * all were successfully loaded.
     * @param types The types to load.
     * @param stack The stack frame.
     * @return Whether all of the given types were loaded.
     * @throws DebugException
     */
    public static boolean tryToLoadTypes(Collection<String> types, IJavaStackFrame stack) throws DebugException {
    	StringBuilder sb = new StringBuilder();
    	sb.append("{\nObject _$o;\n");
    	for (String typeName: types)
    		sb.append("_$o = ").append(getClassLoadExpression(typeName)).append(";\n");
    	sb.append("}");
    	String evalStr = sb.toString();
    	if (getASTEvaluationEngine(stack).getCompiledExpression(evalStr, stack).hasErrors())
    		return false;
    	try {
			evaluate(evalStr, stack);
			return true;
		} catch (DebugException e) {
			return false;
		}
    }
    
    /**
     * Gets the type of a variable, loading it if necessary.
     * @param var The variable whose type to get.
     * @param stack The current stack frame.
     * @return The type of the given variable.
     */
    public static IJavaType getTypeOfVariableAndLoadIfNeeded(IJavaVariable var, IJavaStackFrame stack) {
    	try {
			return var.getJavaType();
		} catch (DebugException e1) {
			try {
				loadClass(var.getReferenceTypeName(), stack);
				return var.getJavaType();
			} catch (DebugException e2) {
				try {
					loadClass(var.getSignature().substring(1, var.getSignature().length() - 1).replace("/", "."), stack);
					return var.getJavaType();
				} catch (DebugException e3) {
					throw new RuntimeException(e1);
				}
			}
		}
    }
    
    /*
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
    
    /*public static ArrayList<Method> getMethods(Expression expr) {
    	final ArrayList<Method> result = new ArrayList<Method>();
    	expr.accept(new ASTVisitor() {
    		@Override
    		public void postVisit(ASTNode node) {
    			if (node instanceof Expression) {
    				Method method = ExpressionMaker.getMethod((Expression)node);
    				if (method != null)
    					result.add(method);
    			}
    		}
    	});
    	return result;
    }*/
	
	/**
	 * Gets the IMethod associated with the given method.
	 * Note that this is not complete; it can (and often
	 * will) fail to find the correct IMethod and return null.
	 * @param method The method.
	 * @param project The java project.
	 * @return The IMethod associated with the given
	 * method, or null.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	public static IMethod getIMethod(Method method, IJavaProject project) throws DebugException, JavaModelException {
		IType itype = project.findType(method.declaringType().name());
		if (itype == null)
			return null;
		String name = null;
		if (method.isConstructor()) {
			name = getUnqualifiedName(method.declaringType().name());
			int dollar = name.lastIndexOf('$');
			if (dollar != -1)
				name = name.substring(dollar + 1);
		} else
			name = method.name();
		String signature = method.signature();
		int numArgs = method.argumentTypeNames().size();
		IMethod best = null;
		IMethod[] methods = null;
		try {
			methods = itype.getMethods();  // Calling getMethods on some anonymous classes throws an exception....
		} catch (JavaModelException e) {
			return null;
		}
		for (IMethod cur: methods) {
			if (cur.getElementName().equals(name)) {
				if (cur.getNumberOfParameters() != numArgs)
					continue;
				if (cur.getSignature().equals(signature))
					return cur;
				if (best != null)
					best = null;
				best = cur;
			}
		}
		/*String parentName = curType.getSuperclassName();
		if (parentName == null)
			break;
		curType = project.findType(parentName);*/
		return best;
	}
	
	/**
	 * Gets the IField associated with the given field.
	 * @param field The field.
	 * @param project The java project.
	 * @return The IField associated with the given field.
	 * @throws JavaModelException
	 */
	public static IField getIField(Field field, IJavaProject project) throws JavaModelException {
		IType itype = project.findType(field.declaringType().name());
		if (itype == null)
			return null;
		return itype.getField(field.name());
	}
    
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
	
	/**
	 * Gets the message of an exception.
	 * Inspired by org.eclipse.jdt.internal.debug.ui.actions.EvaluateAction.getExceptionMessage.
	 * @param exception The exception whose message we want to get.
	 * @return The message of the given exception.
	 */
    public static String getExceptionMessage(Throwable exception) {
		if (exception instanceof CoreException) {
			CoreException ce = (CoreException)exception;
			Throwable throwable = ce.getStatus().getException();
			if (throwable instanceof InvocationException) {
				ObjectReference ref = ((InvocationException)throwable).exception();
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
    
    /**
     * Gets the name of the given exception.
     * @param exception The exception whose name to get.
     * @return The name of the given exception.
     */
    public static String getExceptionName(Throwable exception) {
    	if (exception instanceof CoreException) {
			Throwable throwable = ((CoreException)exception).getStatus().getException();
			if (throwable instanceof InvocationException)
				return ((InvocationException)throwable).exception().referenceType().name();
			else if (throwable instanceof CoreException)
				return getExceptionName(throwable);
		}
		return exception.getClass().toString(); 
    }
    
    /**
     * Gets all the public subtypes of the given type that
     * are in the same package or a subpackage.
     * @param type The type.
     * @param project The project.
     * @param stack The stack frame.
     * @param target The debug target.
     * @param typeCache The type cache.
     * @param monitor The progress monitor.
     * @param taskName The name of the task on the progress monitor.
     * @return All the subtypes of the given type that are
     * in the same package or a subpackage.
     * @throws JavaModelException
     * @throws DebugException
     */
    public static List<IJavaType> getPublicSubtypesInSamePackage(IJavaType type, IJavaProject project, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache, IProgressMonitor monitor, String taskName) throws JavaModelException, DebugException {
    	IType itype = project.findType(type.getName());
    	if (itype == null)
    		return Arrays.asList(new IJavaType[] { type });
    	String packageName = itype.getPackageFragment().getElementName();
    	IType[] subitypes = itype.newTypeHierarchy(project, null).getAllSubtypes(itype);
		// Optimization: pre-load all the types.
		List<String> subtypeNames = new ArrayList<String>(subitypes.length);
		for (IType subitype: subitypes)
			if (!subitype.isAnonymous() && subitype.getPackageFragment().getElementName().startsWith(packageName) && Flags.isPublic(subitype.getFlags()))
				subtypeNames.add(getFullITypeName(subitype));
		tryToLoadTypes(subtypeNames, stack);
		// Get the actual types.
		monitor = SubMonitor.convert(monitor, taskName + ": finding subtypes", subtypeNames.size());
    	List<IJavaType> subtypes = new ArrayList<IJavaType>();
    	subtypes.add(type);
    	for (String subtypeName: subtypeNames)  {
    		if (monitor.isCanceled())
    			return subtypes;
    		IJavaType subtype = getFullyQualifiedTypeIfExists(subtypeName, stack, target, typeCache);  // This works with $-qualified names and fails for nested types with .-qualified names, so we use the former.
    		if (subtype != null)
    			subtypes.add(subtype);
    		monitor.worked(1);
    	}
    	return subtypes;
    }
    
    /**
     * Gets the fully-qualified name (using $) of the given type.
     * @param type The type.
     * @return The fully-qualified name (using $) of the given type.
     */
    private static String getFullITypeName(IType type) {
		String typeName = type.getPackageFragment().getElementName();
		if (!typeName.isEmpty())
			typeName += ".";
		typeName += type.getTypeQualifiedName('$');
		return typeName;
    }

}
