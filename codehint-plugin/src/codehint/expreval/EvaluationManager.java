package codehint.expreval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import com.sun.jdi.Method;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.effects.Effect;
import codehint.exprgen.ExpressionMaker;
import codehint.exprgen.Result;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.TypedExpression;
import codehint.exprgen.ValueCache;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.StateProperty;
import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;
import codehint.utils.Pair;
import codehint.utils.Utils;

/**
 * Class for evaluating expressions.
 */
public final class EvaluationManager {
	
	private final static int BATCH_SIZE = 100;

	private final static String IMPL_NAME = "codehint.CodeHintImpl";
	private final static String IMPL_QUALIFIER = IMPL_NAME + ".";
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
	
	public static class EvaluationError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public EvaluationError(String msg) {
			super(msg);
		}
		
	}
	
	private final boolean isFreeSearch;
	private final IJavaStackFrame stack;
	private final IAstEvaluationEngine engine;
    private final ValueCache valueCache;
    private final TimeoutChecker timeoutChecker;
	private final IJavaDebugTarget target;
	private final IJavaThread thread;
	private final ExpressionMaker expressionMaker;
	private final SubtypeChecker subtypeChecker;
	private final IJavaClassType implType;
	private final IJavaFieldVariable validField;
	private final IJavaFieldVariable toStringsField;
	private final IJavaFieldVariable valueCountField;
	private final IJavaFieldVariable fullCountField;
	private final IJavaFieldVariable methodResultsField;
	// As an optimization, we cache expressions that crash and do not evaluate them again.
	private final Set<String> crashingExpressions;
	
	private InitialSynthesisDialog synthesisDialog;
	private IProgressMonitor monitor;
	private String validVal;
	private String preVarsString;
	private String propertyPreconditions;
	private Map<String, Integer> methodResultsMap;
	private int skipped;
	
	public EvaluationManager(boolean isFreeSearch, IJavaStackFrame stack, ExpressionMaker expressionMaker, SubtypeChecker subtypeChecker, TypeCache typeCache, ValueCache valueCache, TimeoutChecker timeoutChecker) {
		this.isFreeSearch = isFreeSearch;
		this.stack = stack;
		this.engine = EclipseUtils.getASTEvaluationEngine(stack);
		this.valueCache = valueCache;
		this.timeoutChecker = timeoutChecker;
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.thread = (IJavaThread)stack.getThread();
		this.expressionMaker = expressionMaker;
		this.subtypeChecker = subtypeChecker;
		this.implType = EclipseUtils.loadLibrary(IMPL_NAME, stack, target, typeCache);
		try {
			this.validField = implType.getField("valid");
			this.toStringsField = implType.getField("toStrings");
			this.valueCountField = implType.getField("valueCount");
			this.fullCountField = implType.getField("fullCount");
			this.methodResultsField = implType.getField("methodResults");
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.crashingExpressions = new HashSet<String>();
		this.skipped = 0;
	}

	/**
	 * Evaluates the given expressions and returns a list
	 * of non-crashing expressions that satisfy the given
	 * property (or all that do not crash if it is null).
	 * @param exprs The expressions to evaluate.
	 * @param property The desired property, or null if there is none.
	 * @param varType The type of the variable being assigned, or null
	 * if there is none.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor a progress monitor, or null if progress reporting and
	 * cancellation are not desired.  The caller should not allocate a new
	 * progress monitor; this method will do so.
     * @return a list of non-crashing expressions that satisfy
     * the given property (or all that do not crash if it is null).
	 */
	public ArrayList<FullyEvaluatedExpression> evaluateExpressions(ArrayList<? extends TypedExpression> exprs, Property property, IJavaType varType, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		try {
			this.synthesisDialog = synthesisDialog;
			validVal = property == null ? "true" : property.getReplacedString("_$curValue", stack);
			preVarsString = getPreVarsString(stack, property);
			PropertyPreconditionFinder pf = new PropertyPreconditionFinder();
    		EclipseUtils.parseExpr(parser, validVal).accept(pf);
    		propertyPreconditions = property instanceof ValueProperty ? "" : pf.getPreconditions();  // TODO: This will presumably fail if the user does their own null check.
    		boolean validateStatically = canEvaluateStatically(property);
			Map<String, ArrayList<TypedExpression>> expressionsByType = getNonKnownCrashingExpressionByType(exprs);
			int numExpressions = Utils.getNumValues(expressionsByType);
			this.monitor = SubMonitor.convert(monitor, "Expression evaluation", numExpressions);
			ArrayList<FullyEvaluatedExpression> validExprs = new ArrayList<FullyEvaluatedExpression>(numExpressions);
			for (Map.Entry<String, ArrayList<TypedExpression>> expressionsOfType: expressionsByType.entrySet()) {
				String type = EclipseUtils.sanitizeTypename(expressionsOfType.getKey());
				boolean arePrimitives = !"Object".equals(type);
				String valuesArrayName = getValuesArrayName(type);
				IJavaFieldVariable valuesField = implType.getField(valuesArrayName);
				if (property != null && varType != null && "Object".equals(type))  // The pdspec might call methods on the objects, so we need their actual types.
					type = EclipseUtils.sanitizeTypename(varType.getName());
				evaluateExpressions(expressionsOfType.getValue(), validExprs, type, arePrimitives, property, validateStatically, valuesField);
			}
			return validExprs;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean canEvaluateStatically(Property property) throws DebugException {
		return property == null || property instanceof PrimitiveValueProperty || property instanceof TypeProperty || (property instanceof ValueProperty && ((ValueProperty)property).getValue().isNull()) || (property instanceof ObjectValueProperty && "java.lang.String".equals(((ObjectValueProperty)property).getValue().getJavaType().getName())) || (property instanceof StateProperty && "true".equals(((StateProperty)property).getPropertyString()));
	}
	
	/**
	 * Splits the given expressions by their type, either object or primitives.
	 * We do this so that we can evaluate as many expressions together as possible
	 * by storing them into a shared array.  If we used an array of objects for
	 * everything, we would box primitives, which modifies them.  So we put all objects
	 * in an array of objects and primitives in arrays of their own.
	 * It also filters out expressions that we know will crash.
	 */
	private Map<String, ArrayList<TypedExpression>> getNonKnownCrashingExpressionByType(ArrayList<? extends TypedExpression> exprs) throws DebugException {
		Map<String, ArrayList<TypedExpression>> expressionsByType = new HashMap<String, ArrayList<TypedExpression>>();
		for (TypedExpression expr: exprs) {
    		if (!crashingExpressions.contains(expr.getExpression().toString())) {
				IJavaType type = expr.getType();
				String typeName = type == null ? null : EclipseUtils.isPrimitive(type) ? type.getName() : "Object";
				if (!expressionsByType.containsKey(typeName))
					expressionsByType.put(typeName, new ArrayList<TypedExpression>());
				expressionsByType.get(typeName).add(expr);
    		}
		}
		// Nulls have a null type, so put them with the objects.
		if (expressionsByType.containsKey(null)) {
			ArrayList<TypedExpression> nulls = expressionsByType.remove(null);
			if (!expressionsByType.containsKey("Object"))
				expressionsByType.put("Object", new ArrayList<TypedExpression>());
			expressionsByType.get("Object").addAll(nulls);
		}
		return expressionsByType;
	}

	/**
	 * Gets a string whose evaluation will cache the results of
	 * pre-state variables used in the given pdspec.
	 * @param stack The current stack frame.
	 * @param property The current pdspec.
	 * @return A string whose evaluation will cache the results
	 * of pre-state variables used in the given pdspec.
	 * @throws DebugException
	 */
	private static String getPreVarsString(IJavaStackFrame stack, Property property) throws DebugException {
		if (property instanceof StateProperty) {
			StringBuilder expressionsStr = new StringBuilder();
			for (String preVar: ((StateProperty)property).getPreVariables(stack))
				expressionsStr.append(EclipseUtils.sanitizeTypename(stack.findVariable(preVar).getJavaType().getName())).append(" ").append(StateProperty.getRenamedVar(preVar)).append(" = ").append(preVar).append(";\n");
			return expressionsStr.toString();
		} else
			return "";
	}
	
	/**
	 * Gets the names of the array of values in CodeHintImpl
	 * to use for the given type.
	 * @param type The name of the type (either Object or a primitive).
	 * @return The name of the array to use.
	 */
	private static String getValuesArrayName(String type) {
		if ("Object".equals(type))
			return "objects";
		else
			return type + 's';
	}
	
	/**
	 * Evaluates the given expressions.
	 * @param exprs The expressions to evaluate
	 * @param validExprs The results of the evaluations of the given
	 * expressions that do not crash and satisfy the current pdspec.
	 * @param type The static type of the desired expression.
	 * @param arePrimitives Whether the expressions are primitives.
	 * @param property The pdspec.
	 * @param validateStatically Whether we can evaluate the pdspec
	 * ourselves or must have the child evaluation do it.
	 * @param valuesField The field of the proper type to store the
	 * results of the given expressions.
	 */
	private void evaluateExpressions(ArrayList<TypedExpression> exprs, ArrayList<FullyEvaluatedExpression> validExprs, String type, boolean arePrimitives, Property property, boolean validateStatically, IJavaFieldVariable valuesField) {
		try {
			boolean hasPropertyPrecondition = propertyPreconditions.length() > 0;
			String valuesArrayName = valuesField.getName();
			for (int startIndex = 0; startIndex < exprs.size(); ) {
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				ArrayList<Integer> evalExprIndices = new ArrayList<Integer>();
				int numEvaluated = 0;
				Map<String, Integer> temporaries = new HashMap<String, Integer>();
				StringBuilder expressionsStr = new StringBuilder();
				
				// Build and evaluate the evaluation string.
				// TODO: If the user has variables with the same names as the ones I introduce, this will crash....
				expressionsStr.append(preVarsString);
				int i;
		    	for (i = startIndex; i < exprs.size() && numEvaluated < BATCH_SIZE; i++)
		    		numEvaluated = buildStringForExpression(exprs.get(i), i, expressionsStr, arePrimitives, validateStatically, hasPropertyPrecondition, evalExprIndices, numEvaluated, temporaries, valuesArrayName);
		    	DebugException error = null;
		    	if (numEvaluated > 0) {
			    	finishBuildingString(expressionsStr, numEvaluated, type, arePrimitives, validateStatically, valuesArrayName);
			    	
			    	String finalStr = expressionsStr.toString();
			    	ICompiledExpression compiled = engine.getCompiledExpression(finalStr, stack);
			    	if (compiled.hasErrors()) {
			    		handleCompileFailure(exprs, startIndex, i, compiled, type);
			    		continue;
			    	}
			    	timeoutChecker.startEvaluating(fullCountField);
			    	IEvaluationResult result = Evaluator.evaluateExpression(compiled, engine, stack);
			    	timeoutChecker.stopEvaluating();
			    	error = result.getException();
		    	}
	
		    	// Get the results of the evaluation.
		    	int work = i - startIndex;
		    	int numToSkip = 0;
		    	if (error != null) {
					int fullCount = ((IJavaPrimitiveValue)fullCountField.getValue()).getIntValue();
					int valueCount = ((IJavaPrimitiveValue)valueCountField.getValue()).getIntValue();
		    		int crashingIndex = evalExprIndices.get(fullCount);
		    		Expression crashedExpr = exprs.get(crashingIndex).getExpression();
					if (valueCount == fullCount || validateStatically)  // Ensure we crashed on the expression and not the pdspec.
						crashingExpressions.add(crashedExpr.toString());
		    		work = crashingIndex - startIndex;
		    		numToSkip = skipLikelyCrashes(exprs, error, crashingIndex, crashedExpr);
		    	}
		    	if (work > 0) {
		    		ArrayList<FullyEvaluatedExpression> newResults = getResultsFromArray(exprs, property, valuesField, startIndex, work, numEvaluated, validateStatically);
			    	reportResults(newResults);
			    	validExprs.addAll(newResults);
		    	}
		    	/*System.out.println("Evaluated " + (work + numToSkip) + " expressions.");
		    	if (hasError)
		    		System.out.println("Crashed on " + exprs.get(startIndex + count));*/
		    	monitor.worked(work + numToSkip);
		    	startIndex += work + numToSkip;
			}
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Builds a string that will evaluate the given expression and pdspec
	 * and stores it in the given StringBuilder.  Returns the numEvaluated
	 * variable if the variable will not be evaluated and numEvaluated + 1
	 * if it will.
	 * @param curTypedExpr The current expression.
	 * @param i The index of the current expression in the list of all
	 * expressions to be evaluated.
	 * @param expressionsStr The current evaluation string.  The new string
	 * that evaluates this expression will be appended to this.
	 * @param isPrimitive Whether the expression is a primitive.
	 * @param validateStatically Whether we can evaluate the pdspec ourselves
	 * or must have the child evaluation do it.
	 * @param hasPropertyPrecondition Whether the current pdspec has a
	 * precondition.
	 * @param evalExprIndices A list that maps indices in the evaluation
	 * string into indices in the list of all expressions.
	 * @param numEvaluated The number of expressions the current expressionsStr
	 * will evaluate.
	 * @param temporaries A map of the temporaries being used in the current
	 * evaluation string.
	 * @param valuesArrayName The name of the field that will store the output
	 * values.
	 * @return The number of expressions that the potentially-modified
	 * expressionsStr will evaluate.
	 * @throws DebugException
	 */
	private int buildStringForExpression(TypedExpression curTypedExpr, int i, StringBuilder expressionsStr, boolean isPrimitive, boolean validateStatically, boolean hasPropertyPrecondition, ArrayList<Integer> evalExprIndices, int numEvaluated, Map<String, Integer> temporaries, String valuesArrayName) throws DebugException {
		Expression curExpr = curTypedExpr.getExpression();
		ValueFlattener valueFlattener = new ValueFlattener(temporaries, expressionMaker, valueCache);
		String curExprStr = valueFlattener.getResult(curExpr);
		IJavaValue curValue = curTypedExpr.getValue();
		if (curValue == null || !validateStatically) {
			StringBuilder curString = new StringBuilder();
			for (Map.Entry<String, Pair<Integer, String>> newTemp: valueFlattener.getNewTemporaries().entrySet()) {
				curString.append(" ").append(newTemp.getValue().second).append(" _$tmp").append(newTemp.getValue().first).append(" = (").append(newTemp.getValue().second).append(")").append(IMPL_QUALIFIER).append("methodResults[").append(methodResultsMap.get(newTemp.getKey())).append("];\n");
				temporaries.put(newTemp.getKey(), newTemp.getValue().first);
			}
			String curRHSStr = curExprStr;
			if (isPrimitive && curValue != null)
				curRHSStr = EclipseUtils.javaStringOfValue(curValue, stack);
			//curString.append(" // ").append(curExpr.toString()).append("\n");
			String valueStr = "_$curValue";
			if (!validateStatically || !isPrimitive)
				curString.append(" _$curValue = ").append(curRHSStr).append(";\n");
			else
				valueStr = curRHSStr;
			if (!validateStatically) {
				curString.append(" ").append(IMPL_QUALIFIER).append("valueCount = ").append(numEvaluated + 1).append(";\n ");
				if (hasPropertyPrecondition)
					curString.append("if (" + propertyPreconditions + ") {\n ");
				curString.append("_$curValid = ").append(validVal).append(";\n ");
				curString.append(IMPL_QUALIFIER).append("valid[").append(numEvaluated).append("] = _$curValid;\n");
			}
			curString.append(" ").append(IMPL_QUALIFIER).append(valuesArrayName).append("[").append(numEvaluated).append("] = ").append(valueStr).append(";\n ");
			if (!isPrimitive) {
				if (!validateStatically)
					curString.append("if (_$curValid)\n  ");
				curString.append(IMPL_QUALIFIER).append("toStrings[").append(numEvaluated).append("] = ").append(getToStringGetter(curTypedExpr)).append(";\n ");
			}
			if (hasPropertyPrecondition && !validateStatically)
				curString.append(" }\n ");
			curString.append(IMPL_QUALIFIER).append("fullCount = ").append(numEvaluated + 1).append(";\n");
			expressionsStr.append("\n");
			expressionsStr.append(curString.toString());
			evalExprIndices.add(i);
			numEvaluated++;
		}
		return numEvaluated;
	}

	/**
	 * Finishes building the evaluation string by prepending
	 * some initialization information.
	 * @param expressionsStr The current evaluation string,
	 * which will be modified.
	 * @param numEvaluated The number of expressions this
	 * evaluation string modifies.
	 * @param type The type of the expressions being evaluated.
	 * @param arePrimitives Whether the expressions being
	 * evaluated are primitives.
	 * @param validateStatically Whether we can evaluate the
	 * pdspec ourselves or must have the child evaluation do it.
	 * @param valuesArrayName The name of the field that will
	 * store the output values.
	 */
	private static void finishBuildingString(StringBuilder expressionsStr, int numEvaluated, String type, boolean arePrimitives, boolean validateStatically, String valuesArrayName) {
		String newTypeString = "[" + numEvaluated + "]";
		if (type.contains("[]")) {  // If this is an array type, we must specify our new size as the first array dimension, not the last one.
		    int index = type.indexOf("[]");
		    newTypeString = type.substring(0, index) + newTypeString + type.substring(index); 
		} else
		    newTypeString = type + newTypeString;
		StringBuilder prefix = new StringBuilder();
		prefix.append("{\n");
		prefix.append(IMPL_QUALIFIER).append(valuesArrayName).append(" = new ").append(newTypeString).append(";\n");
		if (!validateStatically)
			prefix.append(IMPL_QUALIFIER).append("valid = new boolean[").append(numEvaluated).append("];\n");
		else
			prefix.append(IMPL_QUALIFIER).append("valid = null;\n");
		if (!arePrimitives)
			prefix.append(IMPL_QUALIFIER).append("toStrings = new String[").append(numEvaluated).append("];\n");
		else
			prefix.append(IMPL_QUALIFIER).append("toStrings = null;\n");
		prefix.append(IMPL_QUALIFIER).append("valueCount = 0;\n");
		prefix.append(IMPL_QUALIFIER).append("fullCount = 0;\n");
		if (!validateStatically || !arePrimitives)
			prefix.append(type).append(" _$curValue;\n");
		if (!validateStatically)
			prefix.append("boolean _$curValid;\n");
		expressionsStr.insert(0, prefix.toString());
		expressionsStr.append("}");
	}

	/**
	 * Handles an evaluation string that does not compile
	 * by testing each string individually and removing
	 * those that do not compile from the list of expressions,
	 * as they are presumably the result of generic methods.
	 * @param exprs The list of all expressions being
	 * evaluated.  The expressions that do not compile in the
	 * given range will be removed.
	 * @param startIndex The starting index of the expressions
	 * to check.
	 * @param i The number of expressions to check.
	 * @param compiled The result of the compilation.
	 * @param type The type of the expressions being evaluated.
	 * @throws DebugException
	 */
	private void handleCompileFailure(ArrayList<TypedExpression> exprs, int startIndex, int i, ICompiledExpression compiled, String type) throws DebugException {
		// If we are doing a search with an unconstrained type, the pdspec might crash on certain types, so filter those.
		if (isFreeSearch) {
			String initValue;
			if ("boolean".equals(type))
				initValue = "false";
			else if ("char".equals(type))
				initValue = "' '";
			else if ("byte".equals(type) || "short".equals(type) || "int".equals(type) || "long".equals(type) || "float".equals(type) || "double".equals(type))
				initValue = "0";
			else
				initValue = "null";
			if (engine.getCompiledExpression(type + " _$curValue = " + initValue + ";  boolean _$curValid = " + validVal + ";", stack).hasErrors()) {
				// The pdspec crashed on all things of this type.
				for (int j = i - 1; j >= startIndex; j--)
					exprs.remove(j);
				monitor.worked(exprs.size());
				return;
			}
		}
		// Check the expressions one-by-one and remove those that crash.
		// We can crash thanks to generics and erasure (e.g., by passing an Object to List<String>.set).
		int numDeleted = 0;
		Map<String, Integer> temporaries = new HashMap<String, Integer>(0);
		for (int j = i - 1; j >= startIndex; j--) {
			// We need to get the flattened string not the actual string, since our temporaries can lose type information.  E.g., foo(bar(x),baz) might compile when storing bar(x) in a temporary with an erased type will not.
			ValueFlattener valueFlattener = new ValueFlattener(temporaries, expressionMaker, valueCache);
			String flattenedExprStr = valueFlattener.getResult(exprs.get(j).getExpression());
			StringBuilder curString = new StringBuilder();
			for (Map.Entry<String, Pair<Integer, String>> newTemp: valueFlattener.getNewTemporaries().entrySet())
				curString.append(newTemp.getValue().second).append(" _$tmp").append(newTemp.getValue().first).append(" = (").append(newTemp.getValue().second).append(")").append(IMPL_QUALIFIER).append("methodResults[").append(methodResultsMap.get(newTemp.getKey())).append("];\n");
			curString.append(flattenedExprStr).append(";\n ");
			if (engine.getCompiledExpression(curString.toString(), stack).hasErrors()) {
				crashingExpressions.add(exprs.get(j).getExpression().toString());
				exprs.remove(j);
				numDeleted++;
				//System.out.println(exprStr + " does not compile.");
			}
		}
		if (numDeleted == 0)  // In this case, the error is probably our fault and not due to erasure.
			throw new EvaluationError("Evaluation error: " + "The following errors were encountered during evaluation.\n\n" + EclipseUtils.getCompileErrors(compiled));
		monitor.worked(numDeleted);
	}
	
	/**
	 * Gets a string whose evaluation will get a String
	 * representation of the result of the given expression.
	 * @param expr The expression of whose result we want
	 * a string representation.
	 * @return A String representation of the value of the
	 * given expression.
	 * @throws DebugException 
	 */
	private static String getToStringGetter(TypedExpression expr) throws DebugException {
		String nullCheck = "_$curValue == null ? \"null\" : ";
		if (expr.getType() instanceof IJavaArrayType)
			return nullCheck + "java.util.Arrays.toString((" + EclipseUtils.sanitizeTypename(expr.getType().getName()) + ")_$curValue)";
		else
			return nullCheck + "_$curValue.toString()";
	}
	
	/**
	 * Gets the fully evaluated expressions of those expressions
	 * whose execution did not crash and that produce a
	 * valid result.  See evaluateExpressions for the
	 * string on whose evaluation this is called.
	 * @param exprs The expressions that were evaluated.
	 * @param valuesField The field of the proper type to store the
	 * results of the given expressions.
	 * @param count The number of expressions that were successfully
	 * evaluated.
	 * @return a list of expressions containing those that satisfy the
	 * given property (or all that do not crash if it is null).
	 * @throws DebugException
	 */
	private ArrayList<FullyEvaluatedExpression> getResultsFromArray(ArrayList<TypedExpression> exprs, Property property, IJavaFieldVariable valuesField, int startIndex, int count, int numEvaluated, boolean validateStatically) throws DebugException {
		ArrayList<FullyEvaluatedExpression> validExprs = new ArrayList<FullyEvaluatedExpression>();
		IJavaValue valuesFieldValue = (IJavaValue)valuesField.getValue();
		IJavaValue[] values = numEvaluated == 0 || valuesFieldValue.isNull() ? null : ((IJavaArray)valuesFieldValue).getValues();
		IJavaValue validFieldValue = (IJavaValue)validField.getValue();
		IJavaValue[] valids = numEvaluated == 0 || validFieldValue.isNull() ? null : ((IJavaArray)validFieldValue).getValues();
		IJavaValue toStringsFieldValue = (IJavaValue)toStringsField.getValue();
		IJavaValue[] toStrings = numEvaluated == 0 || toStringsFieldValue.isNull() ? null : ((IJavaArray)toStringsFieldValue).getValues();
		int evalIndex = 0;
		for (int i = 0; i < count; i++) {
			TypedExpression typedExpr = exprs.get(startIndex + i);
			IJavaValue curValue = typedExpr.getValue() != null ? typedExpr.getValue() : values[evalIndex];
			boolean valid = false;
			String validResultString = null;
			if (property == null) {
				valid = true;
				validResultString = getResultString(typedExpr, curValue, toStrings, evalIndex);
			} else if (property instanceof PrimitiveValueProperty) {
				valid = curValue.toString().equals(((PrimitiveValueProperty)property).getValue().toString());
				if (valid)
					validResultString = getJavaString(typedExpr, curValue);
    		} else if (property instanceof TypeProperty) {
				valid = !curValue.isNull() && subtypeChecker.isSubtypeOf(curValue.getJavaType(), ((TypeProperty)property).getType());  // null is not instanceof Object
				if (valid)
					validResultString = getJavaString(typedExpr, curValue);
    		} else if (property instanceof ValueProperty && ((ValueProperty)property).getValue().isNull()) {
    			valid = curValue.isNull();
    			validResultString = "null";
    		} else if (property instanceof ObjectValueProperty && "java.lang.String".equals(((ObjectValueProperty)property).getValue().getJavaType().getName())) {  // The property's value cannot be null because of the previous special case.
    			valid = ((ObjectValueProperty)property).getValue().toString().equals(curValue.toString());
    			if (valid)
    				validResultString = getJavaString(typedExpr, curValue);
    		} else if (property instanceof StateProperty && "true".equals(((StateProperty)property).getPropertyString())) {
    			valid = true;
				validResultString = getResultString(typedExpr, curValue, toStrings, evalIndex);
    		} else {
    			valid = "true".equals(valids[evalIndex].toString());
    			if (valid)
    				validResultString = getResultString(typedExpr, curValue, toStrings, evalIndex);
    		}
			if (valid)
				validExprs.add(new FullyEvaluatedExpression(typedExpr.getExpression(), typedExpr.getType(), new Result(curValue, typedExpr.getResult() == null ? null : typedExpr.getResult().getEffects(), valueCache, thread), validResultString));
			if (typedExpr.getValue() == null || !validateStatically)
				evalIndex++;
		}
		return validExprs;
	}
	
	/**
	 * Gets a String representing the given expression and value.  We
	 * use the given array of computed toStrings if it is non-null,
	 * and we enclose Strings in quotes when necessary.
	 * @param expr The expression.
	 * @param curValue The value whose String representation we want.
	 * @param toStrings An array of the computed toStrings of value,
	 * or null if we did not compute anything.
	 * @param evalIndex The index of the current value in the toStrings
	 * array if it is non-null.
	 * @return A String representation of the given expression and value.
	 * @throws DebugException
	 */
	private String getResultString(TypedExpression expr, IJavaValue curValue, IJavaValue[] toStrings, int evalIndex) throws DebugException {
		if (toStrings == null)
			return getJavaString(expr, curValue);
		else {
			String result = toStrings[evalIndex].getValueString();
			if (!curValue.isNull() && "java.lang.String".equals(curValue.getJavaType().getName()))
				result = "\"" + result + "\"";
			return result;
		}
	}
	
	/**
	 * Gets a String representation of the given expression and value.
	 * @param expr The expression.
	 * @param curValue The value of the expression.
	 * @return A string representation of the given expression and value.
	 * @throws DebugException
	 */
	private String getJavaString(TypedExpression expr, IJavaValue curValue) throws DebugException {
		return expressionMaker.getToStringWithEffects(expr, curValue);
	}

	/**
	 * Records the given expressions in the synthesis dialog,
	 * or does nothing if there is no dialog.
	 * @param results The expressions to record.
	 */
	private void reportResults(final ArrayList<FullyEvaluatedExpression> results) {
		if (synthesisDialog != null && !results.isEmpty())
			synthesisDialog.addExpressions(results);
	}

	/**
	 * Heuristically skips expressions that are similar to an
	 * expression that just crashed and thus are likely to
	 * crash themselves.
	 * @param exprs All of the expressions being evaluated.
	 * @param error The exception thrown by the execution of
	 * the current expression.
	 * @param crashingIndex The index of the crashing expression.
	 * @param crashedExpr The expression whose execution crashed,
	 * @return The number of expressions starting from crashingIndex
	 * that should be skipped.  This includes the expression that
	 * crashed and so will always be at least one.
	 * @throws DebugException 
	 */
	private int skipLikelyCrashes(ArrayList<TypedExpression> exprs, DebugException error, int crashingIndex, Expression crashedExpr) throws DebugException {
		int numToSkip = 1;
		Method crashedMethod = expressionMaker.getMethod(crashedExpr);
		String errorName = EclipseUtils.getExceptionName(error);
		int numNulls = getNumNulls(crashedExpr);
		if (crashedMethod != null && "java.lang.NullPointerException".equals(errorName) && numNulls > 0) {
			// Only skip method calls that we think will throw a NPE and that have at least one subexpression we know is null.
			while (crashingIndex + numToSkip < exprs.size()) {
				Expression newExpr = exprs.get(crashingIndex + numToSkip).getExpression();
				// Skip calls to the same method with at least as much known nulls.
				if (crashedMethod.equals(expressionMaker.getMethod(newExpr)) && getNumNulls(newExpr) >= numNulls) {
					//System.out.println("Skipping " + newExpr.toString() + ".");
					numToSkip++;
				} else
					break;
			}
		} else if (crashedMethod != null && ("java.lang.ArrayIndexOutOfBoundsException".equals(errorName) || "java.lang.IndexOutOfBoundsException".equals(errorName)) && crashedMethod.argumentTypeNames().size() == 1) {
			// Skip methods that throw out-of-bounds exception if the new value is further from 0.
			IJavaValue argValue = expressionMaker.getExpressionValue((Expression)getArguments(crashedExpr).get(0), getReceiverEffects(crashedExpr));
			if ("int".equals(argValue.getJavaType().getName())) {
				int argVal = ((IJavaPrimitiveValue)argValue).getIntValue();
				while (crashingIndex + numToSkip < exprs.size()) {
					Expression newExpr = exprs.get(crashingIndex + numToSkip).getExpression();
					if (crashedMethod.equals(expressionMaker.getMethod(newExpr))) {
						int curVal = ((IJavaPrimitiveValue)expressionMaker.getExpressionValue((Expression)getArguments(newExpr).get(0), getReceiverEffects(newExpr))).getIntValue();
						if ((argVal < 0 && curVal < 0) || (argVal >= 0 && curVal > argVal)) {
							//System.out.println("Skipping " + newExpr.toString() + ".");
							numToSkip++;
						} else
							break;
					} else
						break;
				}
			}
		} else if (crashedMethod != null && (/*"java.lang.IllegalArgumentException".equals(errorName) || */"java.lang.ClassCastException".equals(errorName))) {
			// Skip wrong type exceptions if the arguments have the same type.
			// Note that I don't need to get the correct effects here because I only care about the type of the arguments.
			List<?> args = getArguments(crashedExpr);
			String[] argTypes = new String[args.size()];
			for (int i = 0; i < argTypes.length; i++)
				argTypes[i] = String.valueOf(expressionMaker.getExpressionValue((Expression)args.get(i), Collections.<Effect>emptySet()).getJavaType());
			exprLoop: while (crashingIndex + numToSkip < exprs.size()) {
				Expression newExpr = exprs.get(crashingIndex + numToSkip).getExpression();
				if (crashedMethod.equals(expressionMaker.getMethod(newExpr))) {
					List<?> curArgs = getArguments(newExpr);
					for (int i = 0; i < argTypes.length; i++)
						if (!argTypes[i].equals(String.valueOf(expressionMaker.getExpressionValue((Expression)curArgs.get(i), Collections.<Effect>emptySet()).getJavaType())))
							break exprLoop;
					//System.out.println("Skipping " + newExpr.toString() + ".");
					numToSkip++;
				} else
					break;
			}
		} else if (crashedMethod != null && "codehint.SynthesisSecurityManager$SynthesisSecurityException".equals(errorName)) {
			// Skip calls to methods that try to make illegal system calls.
			while (crashingIndex + numToSkip < exprs.size()) {
				Expression newExpr = exprs.get(crashingIndex + numToSkip).getExpression();
				// Skip calls to the same method.
				if (crashedMethod.equals(expressionMaker.getMethod(newExpr))) {
					//System.out.println("Skipping " + newExpr.toString() + ".");
					numToSkip++;
				} else
					break;
			}
		}
		//System.out.println("Evaluation of " + crashedExpr.toString() + " crashed with message: " + EclipseUtils.getExceptionMessage(error) + ".  Skipping " + numToSkip + ".");
		skipped += numToSkip - 1;
		return numToSkip;
	}
	
	/**
	 * Gets the list of arguments to a call/constructor.
	 * @param expr The call or constructor invocation.
	 * @return The arguments to the given call/constructor.
	 */
	private static List<?> getArguments(Expression expr) {
		if (expr instanceof MethodInvocation)
			return ((MethodInvocation)expr).arguments();
		if (expr instanceof SuperMethodInvocation)
			return ((SuperMethodInvocation)expr).arguments();
		if (expr instanceof ClassInstanceCreation)
			return ((ClassInstanceCreation)expr).arguments();
		throw new RuntimeException("Unexpected expression type: " + expr.getClass());
	}

	/**
	 * Gets the effects of the receiver of the expression
	 * or the empty effect if there is no receiver.
	 * @param crashedExpr
	 * @return
	 */
	private Set<Effect> getReceiverEffects(Expression crashedExpr) {
		if (crashedExpr instanceof MethodInvocation) {
			Expression receiver = ((MethodInvocation)crashedExpr).getExpression();
			if (receiver != null)
				return expressionMaker.getExpressionResult(receiver, Collections.<Effect>emptySet()).getEffects();
		}
		return Collections.<Effect>emptySet();
	}
    
    /**
     * Finds the number of subexpressions of the
     * given expression that are known to be null.
     * @param expr The expression to search.
     * @return The number of subexpressions of the
     * given expression known to be null.
     */
    private int getNumNulls(Expression expr) {
    	final int[] numNulls = new int[] { 0 };
    	expr.accept(new ASTVisitor() {
    		private Set<Effect> curEffects = Collections.<Effect>emptySet();
    		@Override
    		public void postVisit(ASTNode node) {
    			if (node instanceof Expression) {
    				if (node instanceof NullLiteral)
    					numNulls[0]++;
    				else {
    					Result result = expressionMaker.getExpressionResult((Expression)node, curEffects);
    					if (result == null && node instanceof Name)  // TODO: result is null for method/constructor names, but we should have a better check for that
    						return;
    					IJavaValue value = result.getValue().getValue();
    					if (value != null && value.isNull())
        					numNulls[0]++;
    					curEffects = result.getEffects();
    				}
    			}
    		}
    	});
    	return numNulls[0];
    }
    
    /**
     * Caches the results of method calls in the given list.
     * Any call to evaluateExpressions before another call to
     * this method will use the cached value instead of
     * re-evaluating it.
     * @param exprs The list of expressions, which may contain
     * non-call expressions.  This is probably the list of
     * component expressions used to generate the expressions
     * we currently want to evaluate.
     * @throws DebugException
     */
    public void cacheMethodResults(ArrayList<? extends EvaluatedExpression> exprs) throws DebugException {
    	// Find the non-inlined method calls.
    	ArrayList<EvaluatedExpression> calls = new ArrayList<EvaluatedExpression>();
    	for (EvaluatedExpression expr: exprs) {
    		Expression e = expr.getExpression();
    		IJavaValue value = expr.getValue();
    		if ((e instanceof MethodInvocation || e instanceof ClassInstanceCreation || e instanceof SuperMethodInvocation)
    				&& !(value instanceof IJavaPrimitiveValue || value.isNull() || (value instanceof IJavaObject && "Ljava/lang/String;".equals(value.getSignature()))))
    			calls.add(expr);
    	}
		methodResultsMap = new HashMap<String, Integer>();
    	if (!calls.isEmpty()) {  // Cache the method call results so the runtime can use them.
    		IJavaArray newValue = ((IJavaArrayType)methodResultsField.getJavaType()).newInstance(calls.size());
    		for (int i = 0; i < calls.size(); i++) {
    			newValue.setValue(i, calls.get(i).getValue());
    			methodResultsMap.put(calls.get(i).getExpression().toString(), i);
    		}
    		methodResultsField.setValue(newValue);
    	}
    }
	
	/**
	 * Initializes implementation details.
	 */
	public void init() {
		try {
			implType.sendMessage("init", "()V", new IJavaValue[] { }, thread);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Nulls out the CodeHintImpl fields used during evaluation
	 * to free up memory.
	 * @return True if the fields were reset and false if the
	 * evaluation has already terminated.
	 */
	public boolean resetFields() {
		try {
			if (stack.isTerminated())
				return false;
			implType.sendMessage("reset", "()V", new IJavaValue[] { }, thread);
			return true;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Gets the number of expressions whose evaluation crashed.
	 * @return The number of expressions whose evaluation crashed.
	 */
	public int getNumCrashes() {
		return crashingExpressions.size() + skipped;
	}
	
	private static class Evaluator {
		
		private static final Semaphore semaphore = new Semaphore(0);
		private static final IEvaluationResult[] results = new IEvaluationResult[] { null };
		private static final EvaluationListener evaluationListener = new EvaluationListener(results, semaphore);
	
		/**
		 * Evaluates the given expression synchronously.
		 * @param compiled The compiled expression to evaluate.
		 * @param engine The evaluation engine.
		 * @param stack The current stack frame.
		 * @return The result of the evaluation.
		 */
	    public static IEvaluationResult evaluateExpression(ICompiledExpression compiled, IAstEvaluationEngine engine, IJavaStackFrame stack) {
			try {
				engine.evaluateExpression(compiled, stack, evaluationListener, DebugEvent.EVALUATION_IMPLICIT, false);
				semaphore.acquire();
				return results[0];
			} catch (InterruptedException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Thread interrupted.");
			} catch (DebugException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Debug Exception.");
			}
	    }
	    
	    /**
	     * Class to wait for evaluations to finish.
	     */
	    private static class EvaluationListener implements IEvaluationListener {
	
	    	private final IEvaluationResult[] results;
	    	private final Semaphore semaphore;
	    	
	    	public EvaluationListener(IEvaluationResult[] results, Semaphore semaphore) {
	    		this.results = results;
	    		this.semaphore = semaphore;
	    	}
	
			@Override
			public void evaluationComplete(IEvaluationResult result) {
				results[0] = result;
				semaphore.release();  // Up the semaphore to mark that the evaluation finished.
			}
	    	
	    }
	    
	}
    
    /**
     * Finds the preconditions under which we can evaluate the
     * given expression without it crashing, or notes that we
     * can never be sure of this.
     */
    private static class PreconditionFinder extends ASTVisitor {
    	
    	private String preconditions;
    	
    	public PreconditionFinder() {
    		preconditions = "";
    	}
    	
    	protected void add(String str) {
    		if (preconditions.length() == 0)
    			preconditions = str;
    		else
    			preconditions = str + " && " + preconditions;
    	}
    	
    	/**
    	 * Finds the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 * @return the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 */
    	public String getPreconditions() {
    		return preconditions;
    	}
    	
    }

    // TODO: This doesn't work for lambda properties with types, since they always insert a cast.
    /**
     * Gets a simple precondition for a pdspec.
     * It ensures that we do not evaluate the pdspecs
     * for some pdspecs that we know will crash.
     */
    private static class PropertyPreconditionFinder extends PreconditionFinder {
    	
    	@Override
    	public boolean visit(FieldAccess node) {
    		checkForSyntheticVar(node.getExpression());
    		return true;
    	}
    	
    	@Override
    	public boolean visit(MethodInvocation node) {
    		checkForSyntheticVar(node.getExpression());
    		return true;
    	}
    	
    	/**
    	 * If the given node is a synthetic variable,
    	 * we ensure that it is not null before
    	 * evaluating the pdspec. 
    	 * @param node The node to check.
    	 */
    	private void checkForSyntheticVar(Expression node) {
    		if (node instanceof SimpleName) {
    			String name = ((SimpleName)node).getIdentifier();
    			if (name.startsWith("_$"))
    				add(name + " != null");
    		}
    	}
    	
    }

}