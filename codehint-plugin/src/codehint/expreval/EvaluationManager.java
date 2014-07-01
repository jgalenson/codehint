package codehint.expreval;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.eclipse.jdt.core.dom.ASTParser;
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

import codehint.ast.ASTConverter;
import codehint.ast.ASTNode;
import codehint.ast.ASTVisitor;
import codehint.ast.Block;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.MethodInvocation;
import codehint.ast.NullLiteral;
import codehint.ast.SimpleName;
import codehint.ast.Statement;
import codehint.ast.SuperMethodInvocation;
import codehint.dialogs.SynthesisDialog;
import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
import codehint.exprgen.ExpressionEvaluator;
import codehint.exprgen.Result;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.ValueCache;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.Property;
import codehint.property.StateProperty;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;
import codehint.utils.Pair;
import codehint.utils.Utils;

import com.sun.jdi.Method;

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
	private final boolean disableBreakpoints;
	private final IJavaStackFrame stack;
	private final IAstEvaluationEngine engine;
    private final ValueCache valueCache;
    private final TimeoutChecker timeoutChecker;
    private final SideEffectHandler sideEffectHandler;
	private final IJavaDebugTarget target;
	private final IJavaThread thread;
	private final ExpressionEvaluator expressionEvaluator;
	private final SubtypeChecker subtypeChecker;
	private final IJavaClassType implType;
	private final IJavaFieldVariable validField;
	private final IJavaFieldVariable toStringsField;
	private final IJavaFieldVariable valueCountField;
	private final IJavaFieldVariable fullCountField;
	private final IJavaFieldVariable methodResultsField;
	// As an optimization, we cache expressions that crash and do not evaluate them again.
	private final Set<String> crashingStatements;
	private final boolean canUseJar;
	
	private SynthesisDialog synthesisDialog;
	private IProgressMonitor monitor;
	private String validVal;
	private String preVarsString;
	private String propertyPreconditions;
	private Map<String, Integer> methodResultsMap;
	private int skipped;
	
	public EvaluationManager(boolean isFreeSearch, boolean disableBreakpoints, IJavaStackFrame stack, ExpressionEvaluator expressionEvaluator, SubtypeChecker subtypeChecker, TypeCache typeCache, ValueCache valueCache, TimeoutChecker timeoutChecker, SideEffectHandler sideEffectHandler) {
		this.isFreeSearch = isFreeSearch;
		this.disableBreakpoints = disableBreakpoints;
		this.stack = stack;
		this.engine = EclipseUtils.getASTEvaluationEngine(stack);
		this.valueCache = valueCache;
		this.timeoutChecker = timeoutChecker;
		this.sideEffectHandler = sideEffectHandler;
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.thread = (IJavaThread)stack.getThread();
		this.expressionEvaluator = expressionEvaluator;
		this.subtypeChecker = subtypeChecker;
		this.implType = EclipseUtils.loadLibrary(IMPL_NAME, stack, target, typeCache);
		try {
			this.validField = implType.getField("valid");
			this.toStringsField = implType.getField("toStrings");
			this.valueCountField = implType.getField("valueCount");
			this.fullCountField = implType.getField("fullCount");
			this.methodResultsField = implType.getField("methodResults");
			this.canUseJar = !engine.getCompiledExpression(IMPL_QUALIFIER + "valid", stack).hasErrors();  // If the jar is not on the classpath, strings using its type will not compile, so we must do something different.
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.crashingStatements = new HashSet<String>();
		this.skipped = 0;
	}

	/**
	 * Evaluates the given statements and returns a list
	 * of non-crashing statements that satisfy the given
	 * property (or all that do not crash if it is null).
	 * @param stmts The statements to evaluate.
	 * @param property The desired property, or null if there is none.
	 * @param varType The type of the variable being assigned, or null
	 * if there is none.
	 * @param synthesisDialog The synthesis dialog to pass the valid statements,
	 * or null if we should not pass anything.
	 * @param monitor a progress monitor, or null if progress reporting and
	 * cancellation are not desired.  The caller should not allocate a new
	 * progress monitor; this method will do so.
	 * @param taskNameSuffix The suffix of the name of the task to show on the progress monitor.
     * @return a list of non-crashing statements that satisfy
     * the given property (or all that do not crash if it is null).
	 */
	public <T extends Statement> ArrayList<T> evaluateStatements(List<T> stmts, Property property, IJavaType varType, SynthesisDialog synthesisDialog, IProgressMonitor monitor, String taskNameSuffix) {
		try {
			this.synthesisDialog = synthesisDialog;
			validVal = property == null ? "true" : property.getReplacedString("_$curValue", stack);
			preVarsString = getPreVarsString(stack, property);
			PropertyPreconditionFinder pf = new PropertyPreconditionFinder();
    		ASTConverter.parseExpr(parser, validVal).accept(pf);
    		propertyPreconditions = property instanceof ValueProperty ? "" : pf.getPreconditions();  // TODO: This will presumably fail if the user does their own null check.
    		boolean validateStatically = canEvaluateStatically(property);
			Map<String, ArrayList<T>> statementsByType = getNonKnownCrashingStatementsByType(stmts);
			int numStatements = Utils.getNumValues(statementsByType);
			this.monitor = SubMonitor.convert(monitor, "Statement evaluation" + taskNameSuffix, numStatements);
			ArrayList<T> validStmts = new ArrayList<T>(numStatements);
			for (Map.Entry<String, ArrayList<T>> statementsOfType: statementsByType.entrySet()) {
				String type = EclipseUtils.sanitizeTypename(statementsOfType.getKey());
				boolean arePrimitives = !"Object".equals(type);
				String valuesArrayName = getValuesArrayName(type);
				IJavaFieldVariable valuesField = implType.getField(valuesArrayName);
				if (property != null && varType != null && "Object".equals(type))  // The pdspec might call methods on the objects, so we need their actual types.
					type = EclipseUtils.sanitizeTypename(varType.getName());
				if (sideEffectHandler.isHandlingSideEffects() && !validateStatically) {
					// Evaluate statements without effects separately from those with effects, since we can only batch things with the same effects.
					ArrayList<T> noEffects = new ArrayList<T>();
					ArrayList<T> haveEffects = new ArrayList<T>();
					for (T stmt: statementsOfType.getValue()) {
						if (expressionEvaluator.getEffects(stmt, Collections.<Effect>emptySet()).isEmpty())
							noEffects.add(stmt);
						else
							haveEffects.add(stmt);
					}
					evaluateStatements(noEffects, validStmts, type, arePrimitives, property, validateStatically, valuesField);
					evaluateStatements(haveEffects, validStmts, type, arePrimitives, property, validateStatically, valuesField);
				} else
					evaluateStatements(statementsOfType.getValue(), validStmts, type, arePrimitives, property, validateStatically, valuesField);
			}
			return validStmts;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean canEvaluateStatically(Property property) throws DebugException {
		return property == null || property instanceof PrimitiveValueProperty || property instanceof TypeProperty || (property instanceof ValueProperty && ((ValueProperty)property).getValue().isNull()) || (property instanceof ObjectValueProperty && "java.lang.String".equals(((ObjectValueProperty)property).getValue().getJavaType().getName())) || (property instanceof StateProperty && "true".equals(((StateProperty)property).getPropertyString()));
	}
	
	/**
	 * Splits the given statements by their type, either object or primitives.
	 * We do this so that we can evaluate as many statements together as possible
	 * by storing them into a shared array.  If we used an array of objects for
	 * everything, we would box primitives, which modifies them.  So we put all objects
	 * in an array of objects and primitives in arrays of their own.
	 * It also filters out statements that we know will crash.
	 */
	private <T extends Statement> Map<String, ArrayList<T>> getNonKnownCrashingStatementsByType(List<T> stmts) throws DebugException {
		Map<String, ArrayList<T>> statementsByType = new HashMap<String, ArrayList<T>>();
		for (T stmt: stmts) {
			if (!(stmt instanceof Expression))  // Put statements with void type.
				Utils.addToListMap(statementsByType, "void", stmt);
			else  {
				Expression expr = (Expression)stmt;
				if (!crashingStatements.contains(expr.toString())) {
					IJavaType type = expr.getStaticType();
					String typeName = type == null ? null : EclipseUtils.isPrimitive(type) ? type.getName() : "Object";
					Utils.addToListMap(statementsByType, typeName, stmt);
				}
			}
		}
		// Nulls have a null type, so put them with the objects.
		if (statementsByType.containsKey(null)) {
			ArrayList<T> nulls = statementsByType.remove(null);
			if (!statementsByType.containsKey("Object"))
				statementsByType.put("Object", new ArrayList<T>());
			statementsByType.get("Object").addAll(nulls);
		}
		return statementsByType;
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
			StringBuilder statementsStr = new StringBuilder();
			for (String preVar: ((StateProperty)property).getPreVariables())
				statementsStr.append(EclipseUtils.sanitizeTypename(stack.findVariable(preVar).getJavaType().getName())).append(" ").append(StateProperty.getRenamedVar(preVar)).append(" = ").append(preVar).append(";\n");
			return statementsStr.toString();
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
	 * Evaluates the given statements.
	 * @param stmts The statements to evaluate
	 * @param validStmts The results of the evaluations of the given
	 * statements that do not crash and satisfy the current pdspec.
	 * @param type The static type of the desired statement.
	 * @param arePrimitives Whether the statements are primitives.
	 * @param property The pdspec.
	 * @param validateStatically Whether we can evaluate the pdspec
	 * ourselves or must have the child evaluation do it.
	 * @param valuesField The field of the proper type to store the
	 * results of the given statements, or null if there are no output
	 * values (i.e., for non-expressions).
	 */
	private <T extends Statement> void evaluateStatements(ArrayList<T> stmts, ArrayList<T> validStmts, String type, boolean arePrimitives, Property property, boolean validateStatically, IJavaFieldVariable valuesField) {
		try {
			boolean hasPropertyPrecondition = propertyPreconditions.length() > 0;
			boolean propertyUsesLHS = property == null ? true : property.usesLHS();
			String valuesArrayName = valuesField == null ? null : valuesField.getName();
			for (int startIndex = 0; startIndex < stmts.size(); ) {
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				ArrayList<Integer> evalStmtIndices = new ArrayList<Integer>();
				int numEvaluated = 0;
				Map<String, Integer> temporaries = new HashMap<String, Integer>();
				StringBuilder statementsStr = new StringBuilder();
				
				// Build and evaluate the evaluation string.
				// TODO: If the user has variables with the same names as the ones I introduce, this will crash....
				statementsStr.append(preVarsString);
				boolean allAlreadyEvaluated = true;
				Set<Effect> effects = null;
				int i;
		    	for (i = startIndex; i < stmts.size() && numEvaluated < BATCH_SIZE; i++) {
		    		Statement curStmt = stmts.get(i);
		    		Set<Effect> curEffects = null;
	    			Result result = expressionEvaluator.getResult(curStmt, Collections.<Effect>emptySet());
		    		if (sideEffectHandler.isHandlingSideEffects() && result != null)
	    				curEffects = result.getEffects();
		    		if (result == null)
		    			allAlreadyEvaluated = false;
		    		if (effects != null && !effects.equals(curEffects))
		    			break;
					numEvaluated = buildStringForStatement(curStmt, i, statementsStr, arePrimitives, validateStatically, hasPropertyPrecondition, propertyUsesLHS, evalStmtIndices, numEvaluated, temporaries, valuesArrayName);
					if (numEvaluated == 1)
		    			effects = curEffects;
		    	}
		    	boolean isSimple = false;
		    	IJavaValue value = null;
		    	DebugException error = null;
		    	if (numEvaluated > 0) {
		    		String finalStr = null;
		    		if (numEvaluated == 1) {  // Try to optimize the evaluation string if it is only evaluating one statement.
		    			Statement stmt = stmts.get(evalStmtIndices.get(0));
		    			Result result = expressionEvaluator.getResult(stmt, Collections.<Effect>emptySet());
		    			if (result != null && (!(stmt instanceof Expression) || !propertyUsesLHS)) {  // We can optimize things whose result we know and whose value doesn't matter.
		    				finalStr = validVal;  // In these cases our evaluation string is simply the pdspec string.
		    				isSimple = true;
		    			}
		    		}
		    		if (finalStr == null) {
		    			finishBuildingString(statementsStr, numEvaluated, allAlreadyEvaluated, type, arePrimitives, validateStatically, propertyUsesLHS, valuesArrayName);
		    			finalStr = statementsStr.toString();
		    		}
		    		//System.out.println(finalStr);
			    	ICompiledExpression compiled = engine.getCompiledExpression(finalStr, stack);
			    	if (compiled.hasErrors()) {
			    		handleCompileFailure(stmts, startIndex, i, compiled, type);
			    		continue;
			    	}
			    	boolean isHandlingSideEffects = sideEffectHandler.isHandlingSideEffects();
			    	try {
			    		sideEffectHandler.startHandlingSideEffects();
			    		if (effects != null)
			    			SideEffectHandler.redoEffects(effects);
			    		timeoutChecker.startEvaluating(fullCountField);
			    		IEvaluationResult result = Evaluator.evaluateExpression(compiled, engine, stack, disableBreakpoints);
			    		if (isSimple)
			    			value = result.getValue();
				    	error = result.getException();
			    	} finally {
			    		timeoutChecker.stopEvaluating();
			    		if (isHandlingSideEffects && effects != null)  // This should only be true during refinement with effects, in which case there are no existing effects so this should correctly restore us.
			    			SideEffectHandler.undoEffects(effects);
			    		else if (!isHandlingSideEffects)
			    			sideEffectHandler.stopHandlingSideEffects();
			    	}
		    	}
	
		    	// Get the results of the evaluation.
		    	int work = i - startIndex;
		    	int numToSkip = 0;
		    	if (error != null) {
					int fullCount = isSimple ? 0 : ((IJavaPrimitiveValue)fullCountField.getValue()).getIntValue();
					int valueCount = isSimple ? 1 : ((IJavaPrimitiveValue)valueCountField.getValue()).getIntValue();
		    		int crashingIndex = evalStmtIndices.get(fullCount);
		    		Statement crasher = stmts.get(crashingIndex);
					if (valueCount == fullCount || validateStatically)  // Ensure we crashed on the statement and not the pdspec.
						crashingStatements.add(crasher.toString());
		    		work = crashingIndex - startIndex;
		    		numToSkip = skipLikelyCrashes(stmts, error, crashingIndex, crasher);
			    	monitor.worked(numToSkip);
		    	}
		    	if (work > 0) {
		    		ArrayList<T> newResults = getResultsFromArray(stmts, property, value, valuesField, startIndex, work, numEvaluated, validateStatically);
			    	reportResults(newResults);
			    	validStmts.addAll(newResults);
		    	}
		    	/*System.out.println("Evaluated " + (work + numToSkip) + " statements.");
		    	if (hasError)
		    		System.out.println("Crashed on " + stmts.get(startIndex + count));*/
		    	startIndex += work + numToSkip;
			}
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Builds a string that will evaluate the given statement and pdspec
	 * and stores it in the given StringBuilder.  Returns the numEvaluated
	 * variable if the variable will not be evaluated and numEvaluated + 1
	 * if it will.
	 * @param curStmt The current statement.
	 * @param i The index of the current statement in the list of all
	 * statements to be evaluated.
	 * @param statementsStr The current evaluation string.  The new string
	 * that evaluates this statement will be appended to this.
	 * @param isPrimitive Whether the statement is a primitive.
	 * @param validateStatically Whether we can evaluate the pdspec ourselves
	 * or must have the child evaluation do it.
	 * @param hasPropertyPrecondition Whether the current pdspec has a
	 * precondition.
	 * @param propertyUsesLHS Whether the current property uses the LHS.
	 * @param evalStmtIndices A list that maps indices in the evaluation
	 * string into indices in the list of all statements.
	 * @param numEvaluated The number of statements the current statementsStr
	 * will evaluate.
	 * @param temporaries A map of the temporaries being used in the current
	 * evaluation string.
	 * @param valuesArrayName The name of the field that will store the output
	 * values.
	 * @return The number of statements that the potentially-modified
	 * statementsStr will evaluate.
	 * @throws DebugException
	 */
	private int buildStringForStatement(Statement curStmt, int i, StringBuilder statementsStr, boolean isPrimitive, boolean validateStatically, boolean hasPropertyPrecondition, boolean propertyUsesLHS, ArrayList<Integer> evalStmtIndices, int numEvaluated, Map<String, Integer> temporaries, String valuesArrayName) throws DebugException {
		Result curResult = expressionEvaluator.getResult(curStmt, Collections.<Effect>emptySet());
		if (curResult == null || !validateStatically) {
			StringBuilder curString = new StringBuilder();
			ValueFlattener valueFlattener = new ValueFlattener(temporaries, methodResultsMap, expressionEvaluator, valueCache);
			String curStmtStr = valueFlattener.getResult(curStmt);
			if (propertyUsesLHS)
				for (Map.Entry<String, Pair<Integer, String>> newTemp: valueFlattener.getNewTemporaries().entrySet()) {
					curString.append(" ").append(newTemp.getValue().second).append(" _$tmp").append(newTemp.getValue().first).append(" = (").append(newTemp.getValue().second).append(")").append(getQualifier(null)).append("methodResults[").append(methodResultsMap.get(newTemp.getKey())).append("];\n");
					temporaries.put(newTemp.getKey(), newTemp.getValue().first);
				}
			if (isFreeSearch && !isPrimitive)  // Variables now might have different types, so give each evaluation its own scope, but declare temporaries outside that since we reuse them.
				curString.append("{\n");
			String curRHSStr = curStmtStr;
			if (isPrimitive && curResult != null && curStmt instanceof Expression)
				curRHSStr = EclipseUtils.javaStringOfValue(curResult.getValue().getValue(), stack, false);
			//curString.append(" // ").append(curStmt.toString()).append("\n");
			String valueStr = "_$curValue";
			if ((!validateStatically || !isPrimitive) && curStmt instanceof Expression && propertyUsesLHS) {
				if (isFreeSearch && !isPrimitive) {
					IJavaType type = ((Expression)curStmt).getStaticType();
					curString.append(" ").append(type == null ? "Object" : EclipseUtils.sanitizeTypename(type.getName()));
				}
				curString.append(" _$curValue = ").append(curRHSStr).append(";\n");
			} else
				valueStr = curRHSStr;
			if (!validateStatically) {
				if (curResult == null) {
					if (canUseJar)
						curString.append(" ").append(getQualifier(null)).append("valueCount = ").append(numEvaluated + 1).append(";\n ");
					else
						curString.append(" _$valueCountField.setInt(null, ").append(numEvaluated + 1).append(");\n ");
				}
				if (hasPropertyPrecondition)
					curString.append("if (" + propertyPreconditions + ") {\n ");
				/*curString.append("_$curValid = ").append(validVal).append(";\n ");
				curString.append(getQualifier(null)).append("valid[").append(numEvaluated).append("] = _$curValid;\n");*/
				curString.append(getQualifier(null)).append("valid[").append(numEvaluated).append("] = ").append(validVal).append(";\n");
			}
			if (curStmt instanceof Expression && curResult == null)
				curString.append(" ").append(getQualifier(null)).append(valuesArrayName).append("[").append(numEvaluated).append("] = ").append(valueStr).append(";\n ");
			/*else  // We replay the effects before evaluating this, so we do not want to execute the actual statement.
				curString.append(" ").append(valueStr);*/
			/*if (!isPrimitive && curStmt instanceof Expression) {
				if (!validateStatically)
					curString.append("if (_$curValid)\n  ");
				curString.append(getQualifier(null)).append("toStrings[").append(numEvaluated).append("] = ").append(getToStringGetter((Expression)curStmt)).append(";\n ");
			}*/
			if (hasPropertyPrecondition && !validateStatically)
				curString.append(" }\n ");
			if (canUseJar)
				curString.append(getQualifier(null)).append("fullCount = ").append(numEvaluated + 1).append(";\n");
			else
				curString.append("_$fullCountField.setInt(null, ").append(numEvaluated + 1).append(");\n");
			if (isFreeSearch && !isPrimitive)
				curString.append("}\n");
			statementsStr.append("\n");
			statementsStr.append(curString.toString());
			evalStmtIndices.add(i);
			numEvaluated++;
		}
		return numEvaluated;
	}

	/**
	 * Finishes building the evaluation string by prepending
	 * some initialization information.
	 * @param statementsStr The current evaluation string,
	 * which will be modified.
	 * @param numEvaluated The number of statements this
	 * evaluation string modifies.
	 * @param allAlreadyEvaluated Whether all of the statements
	 * evaluated by this string had already been evaluated.
	 * @param type The type of the statements being evaluated.
	 * @param arePrimitives Whether the statements being
	 * evaluated are primitives.
	 * @param validateStatically Whether we can evaluate the
	 * pdspec ourselves or must have the child evaluation do it.
	 * @param propertyUsesLHS Whether the current property uses the LHS.
	 * @param valuesArrayName The name of the field that will
	 * store the output values, or null if there are no output
	 * values (i.e., for non-expressions).
	 */
	private void finishBuildingString(StringBuilder statementsStr, int numEvaluated, boolean allAlreadyEvaluated, String type, boolean arePrimitives, boolean validateStatically, boolean propertyUsesLHS, String valuesArrayName) {
		String newTypeString = "[" + numEvaluated + "]";
		if (type.contains("[]")) {  // If this is an array type, we must specify our new size as the first array dimension, not the last one.
		    int index = type.indexOf("[]");
		    newTypeString = type.substring(0, index) + newTypeString + type.substring(index); 
		} else
		    newTypeString = type + newTypeString;
		StringBuilder prefix = new StringBuilder();
		prefix.append("{\n");
		if (valuesArrayName != null && !allAlreadyEvaluated)
			prefix.append(getQualifier(type + "[]")).append(valuesArrayName).append(" = new ").append(newTypeString).append(";\n");
		if (!validateStatically)
			prefix.append(getQualifier("boolean[]")).append("valid = new boolean[").append(numEvaluated).append("];\n");
		else
			prefix.append(getQualifier("boolean[]")).append("valid = null;\n");
		/*if (!arePrimitives)
			prefix.append(getQualifier("String[]")).append("toStrings = new String[").append(numEvaluated).append("];\n");
		else
			prefix.append(getQualifier("String[]")).append("toStrings = null;\n");*/
		if (!allAlreadyEvaluated)
			prefix.append(getQualifier("int")).append("valueCount = 0;\n");
		prefix.append(getQualifier("int")).append("fullCount = 0;\n");	
		if ((!validateStatically || !arePrimitives) && (!isFreeSearch || arePrimitives) && valuesArrayName != null && propertyUsesLHS)
			prefix.append(type).append(" _$curValue;\n");
		/*if (!validateStatically)
			prefix.append("boolean _$curValid;\n");*/
		if (!canUseJar) {
			// Get the impl jar.
			prefix.append("java.lang.reflect.Field _$implClassField = System.getSecurityManager().getClass().getDeclaredField(\"codeHintImplClass\");\n");
			prefix.append("_$implClassField.setAccessible(true);\n");
			prefix.append("Class _$implClass = (Class)_$implClassField.get(null);\n");
			// Setup local aliases
			if (valuesArrayName != null && !allAlreadyEvaluated)
				prefix.append("_$implClass.getDeclaredField(\"").append(valuesArrayName).append("\").set(null, ").append(valuesArrayName).append(");\n");
			if (!validateStatically)
				prefix.append("_$implClass.getDeclaredField(\"valid\").set(null, valid);\n");
			/*if (!arePrimitives)
				prefix.append("_$implClass.getDeclaredField(\"toStrings\").set(null, toStrings);\n");*/
			prefix.append("Object[] methodResults = (Object[])_$implClass.getDeclaredField(\"methodResults\").get(null);\n");
			// Setup integers.
			prefix.append("java.lang.reflect.Field _$valueCountField = _$implClass.getDeclaredField(\"valueCount\");\n");
			prefix.append("java.lang.reflect.Field _$fullCountField = _$implClass.getDeclaredField(\"fullCount\");\n");
			prefix.append("_$valueCountField.setInt(null, 0);\n");
			prefix.append("_$fullCountField.setInt(null, 0);\n");
		}
		statementsStr.insert(0, prefix.toString());
		statementsStr.append("}");
	}
	
	/**
	 * Gets the qualifier to use for accesses to data structures
	 * used for evaluation.  We need this because if we cannot
	 * access the library jar, we cannot use the real ones and
	 * instead alias them locally.
	 * @param type The type of the field if this is a declaration
	 * and we cannot access the library jar or null if we should
	 * just return the qualifier itself.
	 * @return The qualifier to use for accesses to data structures
	 * used for evaluation, possibly with a type declaration.
	 */
	private String getQualifier(String type) {
		return canUseJar ? IMPL_QUALIFIER : (type == null ? "" : type + " ");
	}

	/**
	 * Handles an evaluation string that does not compile
	 * by testing each string individually and removing
	 * those that do not compile from the list of statements,
	 * as they are presumably the result of generic methods.
	 * @param stmts The list of all statements being
	 * evaluated.  The statements that do not compile in the
	 * given range will be removed.
	 * @param startIndex The starting index of the statements
	 * to check.
	 * @param i The number of statements to check.
	 * @param compiled The result of the compilation.
	 * @param type The type of the statements being evaluated.
	 * @throws DebugException
	 */
	private void handleCompileFailure(ArrayList<? extends Statement> stmts, int startIndex, int i, ICompiledExpression compiled, String type) throws DebugException {
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
			if (!"Object".equals(type) && engine.getCompiledExpression(type + " _$curValue = " + initValue + ";  boolean _$curValid = " + validVal + ";", stack).hasErrors()) {
				// The pdspec crashed on all things of this type.  But we ignore Objects since it might work for some subtypes but not others.
				// TODO: I could optimize this by marking this type as illegal when handling side effects and hence batch sizes are 1.
				for (int j = i - 1; j >= startIndex; j--) {
					//System.out.println(stmts.get(j) + " does not compile with pdspec " + validVal + " in free search.");
					stmts.remove(j);
				}
				monitor.worked(stmts.size());
				return;
			}
		}
		// Check the statements one-by-one and remove those that crash.
		// We can crash thanks to generics and erasure (e.g., by passing an Object to List<String>.set).
		int numDeleted = 0;
		Map<String, Integer> temporaries = new HashMap<String, Integer>(0);
		for (int j = i - 1; j >= startIndex; j--) {
			Statement stmt = stmts.get(j);
			// We need to get the flattened string not the actual string, since our temporaries can lose type information.  E.g., foo(bar(x),baz) might compile when storing bar(x) in a temporary with an erased type will not.
			ValueFlattener valueFlattener = new ValueFlattener(temporaries, methodResultsMap, expressionEvaluator, valueCache);
			String flattenedStmtStr = valueFlattener.getResult(stmts.get(j));
			StringBuilder curString = new StringBuilder();
			curString.append("{\n ");
			for (Map.Entry<String, Pair<Integer, String>> newTemp: valueFlattener.getNewTemporaries().entrySet())
				curString.append(newTemp.getValue().second).append(" _$tmp").append(newTemp.getValue().first).append(" = (").append(newTemp.getValue().second).append(")").append(getQualifier(null)).append("methodResults[").append(methodResultsMap.get(newTemp.getKey())).append("];\n");
			if (stmt instanceof Expression) {
				IJavaType exprType = ((Expression)stmt).getStaticType();
				String typeName = isFreeSearch && "Object".equals(type) ? (exprType == null ? "Object" : EclipseUtils.sanitizeTypename(exprType.getName())) : type;
				curString.append(typeName).append(" _$curValue = ").append(flattenedStmtStr).append(";\n ");
			} else
				curString.append(flattenedStmtStr);
			StringBuilder curStringWithSpec = new StringBuilder(curString.toString());
			curStringWithSpec.append("boolean _$curValid = ").append(validVal).append(";\n}");
			if (engine.getCompiledExpression(curStringWithSpec.toString(), stack).hasErrors()) {
				if (engine.getCompiledExpression(curString.toString() + "\n}", stack).hasErrors())  // Do not mark the statement as crashing if we crash on the pdspec.
					crashingStatements.add(stmts.get(j).toString());
				stmts.remove(j);
				numDeleted++;
				//System.out.println(stmt + " does not compile with pdspec " + validVal + ".");
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
	/*private static String getToStringGetter(Expression expr) throws DebugException {
		String nullCheck = "_$curValue == null ? \"null\" : ";
		if (expr.getStaticType() instanceof IJavaArrayType)
			return nullCheck + "java.util.Arrays.toString((" + EclipseUtils.sanitizeTypename(expr.getStaticType().getName()) + ")_$curValue)";
		else
			return nullCheck + "_$curValue.toString()";
	}*/
	
	/**
	 * Gets the fully evaluated statements of those statements
	 * whose execution did not crash and that produce a
	 * valid result.  See evaluateStatements for the
	 * string on whose evaluation this is called.
	 * @param stmts The statements that were evaluated.
	 * @param property The pdspec.
	 * @param value If this is non-null, it is the valid value and
	 * we only checked one statement whose result we already knew. 
	 * @param valuesField The field of the proper type to store the
	 * results of the given statements.
	 * @param count The number of statements that were successfully
	 * evaluated.
	 * @return a list of statements containing those that satisfy the
	 * given property (or all that do not crash if it is null).
	 * @throws DebugException
	 */
	private <T extends Statement> ArrayList<T> getResultsFromArray(ArrayList<T> stmts, Property property, IJavaValue value, IJavaFieldVariable valuesField, int startIndex, int count, int numEvaluated, boolean validateStatically) throws DebugException {
		ArrayList<T> validStmts = new ArrayList<T>();
		IJavaValue valuesFieldValue = valuesField == null ? null : (IJavaValue)valuesField.getValue();
		IJavaValue[] values = numEvaluated == 0 || valuesFieldValue == null || valuesFieldValue.isNull() ? null : ((IJavaArray)valuesFieldValue).getValues();
		IJavaValue validFieldValue = (IJavaValue)validField.getValue();
		IJavaValue[] valids = numEvaluated == 0 || validFieldValue.isNull() ? null : ((IJavaArray)validFieldValue).getValues();
		IJavaValue toStringsFieldValue = (IJavaValue)toStringsField.getValue();
		IJavaValue[] toStrings = numEvaluated == 0 || toStringsFieldValue.isNull() ? null : ((IJavaArray)toStringsFieldValue).getValues();
		int evalIndex = 0;
		for (int i = 0; i < count; i++) {
			if (monitor.isCanceled())  // We ignore the maximum batch size if everything is already evaluated, so we might have a lot of things here and hence need this check.
				throw new OperationCanceledException();
			T typedStmt = stmts.get(startIndex + i);
			Result initResult = expressionEvaluator.getResult(typedStmt, Collections.<Effect>emptySet());
			IJavaValue curValue = typedStmt instanceof Expression ? (initResult == null ? values[evalIndex] : initResult.getValue().getValue()) : null;
			boolean valid = false;
			String validResultString = null;
			if (property == null) {
				valid = true;
				if (typedStmt instanceof Expression)
					validResultString = getResultString((Expression)typedStmt, curValue, toStrings, evalIndex);
			} else if (property instanceof PrimitiveValueProperty) {
				valid = curValue.toString().equals(((PrimitiveValueProperty)property).getValue().toString());
				if (valid)
					validResultString = getJavaString((Expression)typedStmt, curValue);
    		} else if (property instanceof TypeProperty) {
				valid = !curValue.isNull() && subtypeChecker.isSubtypeOf(curValue.getJavaType(), ((TypeProperty)property).getType());  // null is not instanceof Object
				if (valid)
					validResultString = getJavaString((Expression)typedStmt, curValue);
    		} else if (property instanceof ValueProperty && ((ValueProperty)property).getValue().isNull()) {
    			valid = curValue.isNull();
    			validResultString = "null";
    		} else if (property instanceof ObjectValueProperty && "java.lang.String".equals(((ObjectValueProperty)property).getValue().getJavaType().getName())) {  // The property's value cannot be null because of the previous special case.
    			valid = ((ObjectValueProperty)property).getValue().toString().equals(curValue.toString());
    			if (valid)
    				validResultString = getJavaString((Expression)typedStmt, curValue);
    		} else if (property instanceof StateProperty && "true".equals(((StateProperty)property).getPropertyString())) {
    			valid = true;
    			if (typedStmt instanceof Expression)
    				validResultString = getResultString((Expression)typedStmt, curValue, toStrings, evalIndex);
    		} else {
    			if (value != null && !"V".equals(value.getSignature()))
    				valid = ((IJavaPrimitiveValue)value).getBooleanValue();
    			else
    				valid = "true".equals(valids[evalIndex].toString());
    			if (valid && typedStmt instanceof Expression)
    				validResultString = getResultString((Expression)typedStmt, curValue, toStrings, evalIndex);
    		}
			if (valid) {
				if (initResult == null)
					expressionEvaluator.setResult(typedStmt, new Result(curValue, Collections.<Effect>emptySet(), valueCache, thread), Collections.<Effect>emptySet());
				if (typedStmt instanceof Expression)
					expressionEvaluator.setResultString((Expression)typedStmt, Utils.getPrintableString(validResultString));
				validStmts.add(typedStmt);
			}
			if (initResult == null || !validateStatically)
				evalIndex++;
			monitor.worked(1);
		}
		return validStmts;
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
	private String getResultString(Expression expr, IJavaValue curValue, IJavaValue[] toStrings, int evalIndex) throws DebugException {
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
	private String getJavaString(Expression expr, IJavaValue curValue) throws DebugException {
		return expressionEvaluator.getToStringWithEffects(expr, curValue);
	}

	/**
	 * Records the given statements in the synthesis dialog,
	 * or does nothing if there is no dialog.
	 * @param results The statements to record.
	 */
	private void reportResults(final ArrayList<? extends Statement> results) {
		if (synthesisDialog != null && !results.isEmpty())
			synthesisDialog.addStatements(results);
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
	private int skipLikelyCrashes(ArrayList<? extends Statement> exprs, DebugException error, int crashingIndex, Statement crasher) throws DebugException {
		int numToSkip = 1;
		if (!(crasher instanceof Expression))
			return numToSkip;
		Expression crashedExpr = (Expression)crasher;
		Method crashedMethod = expressionEvaluator.getMethod(crashedExpr);
		String errorName = EclipseUtils.getExceptionName(error);
		int numNulls = getNumNulls(crashedExpr);
		if (crashedMethod != null && "java.lang.NullPointerException".equals(errorName) && numNulls > 0) {
			// Only skip method calls that we think will throw a NPE and that have at least one subexpression we know is null.
			while (crashingIndex + numToSkip < exprs.size()) {
				Statement newStmt = exprs.get(crashingIndex + numToSkip);
				if (!(newStmt instanceof Expression))
					break;
				Expression newExpr = (Expression)newStmt;
				// Skip calls to the same method with at least as much known nulls.
				if (crashedMethod.equals(expressionEvaluator.getMethod(newExpr)) && getNumNulls(newExpr) >= numNulls) {
					//System.out.println("Skipping " + newExpr.toString() + ".");
					numToSkip++;
				} else
					break;
			}
		} else if (crashedMethod != null && ("java.lang.ArrayIndexOutOfBoundsException".equals(errorName) || "java.lang.IndexOutOfBoundsException".equals(errorName)) && crashedMethod.argumentTypeNames().size() == 1) {
			// Skip methods that throw out-of-bounds exception if the new value is further from 0.
			IJavaValue argValue = expressionEvaluator.getValue(getArguments(crashedExpr)[0], getReceiverEffects(crashedExpr));
			if ("int".equals(argValue.getJavaType().getName())) {
				int argVal = ((IJavaPrimitiveValue)argValue).getIntValue();
				while (crashingIndex + numToSkip < exprs.size()) {
					Statement newStmt = exprs.get(crashingIndex + numToSkip);
					if (!(newStmt instanceof Expression))
						break;
					Expression newExpr = (Expression)newStmt;
					if (crashedMethod.equals(expressionEvaluator.getMethod(newExpr))) {
						int curVal = ((IJavaPrimitiveValue)expressionEvaluator.getValue(getArguments(newExpr)[0], getReceiverEffects(newExpr))).getIntValue();
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
			Expression[] args = getArguments(crashedExpr);
			String[] argTypes = new String[args.length];
			for (int i = 0; i < argTypes.length; i++)
				argTypes[i] = String.valueOf(expressionEvaluator.getValue(args[i], Collections.<Effect>emptySet()).getJavaType());
			exprLoop: while (crashingIndex + numToSkip < exprs.size()) {
				Statement newStmt = exprs.get(crashingIndex + numToSkip);
				if (!(newStmt instanceof Expression))
					break;
				Expression newExpr = (Expression)newStmt;
				if (crashedMethod.equals(expressionEvaluator.getMethod(newExpr))) {
					Expression[] curArgs = getArguments(newExpr);
					for (int i = 0; i < argTypes.length; i++)
						if (!argTypes[i].equals(String.valueOf(expressionEvaluator.getValue(curArgs[i], Collections.<Effect>emptySet()).getJavaType())))
							break exprLoop;
					//System.out.println("Skipping " + newExpr.toString() + ".");
					numToSkip++;
				} else
					break;
			}
		} else if (crashedMethod != null && "codehint.SynthesisSecurityManager$SynthesisSecurityException".equals(errorName)) {
			// Skip calls to methods that try to make illegal system calls.
			while (crashingIndex + numToSkip < exprs.size()) {
				Statement newStmt = exprs.get(crashingIndex + numToSkip);
				if (!(newStmt instanceof Expression))
					break;
				Expression newExpr = (Expression)newStmt;
				// Skip calls to the same method.
				if (crashedMethod.equals(expressionEvaluator.getMethod(newExpr))) {
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
	private static Expression[] getArguments(Expression expr) {
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
				return expressionEvaluator.getEffects(receiver, Collections.<Effect>emptySet());
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
    					Result result = expressionEvaluator.getResult((Expression)node, curEffects);
    					if (result == null || result.getValue() == null)  // TODO: result is null for method/constructor names or crashed native calls or after side effects during refinement.
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
     * Any call to evaluateStatements before another call to
     * this method will use the cached value instead of
     * re-evaluating it.
     * @param stmts The list of statements, which may contain
     * non-call statements.  This is probably the list of
     * component statements used to generate the statements
     * we currently want to evaluate.
     * @throws DebugException
     */
    public void cacheMethodResults(List<? extends Statement> stmts) throws DebugException {
    	// Find the non-inlined method calls.
    	ArrayList<Expression> calls = new ArrayList<Expression>();
    	addCalls(stmts, calls);
		methodResultsMap = new HashMap<String, Integer>();
    	if (!calls.isEmpty()) {  // Cache the method call results so the runtime can use them.
    		IJavaArray newValue = ((IJavaArrayType)methodResultsField.getJavaType()).newInstance(calls.size());
    		for (int i = 0; i < calls.size(); i++) {
    			newValue.setValue(i, expressionEvaluator.getValue(calls.get(i), Collections.<Effect>emptySet()));
    			methodResultsMap.put(calls.get(i).toString(), i);
    		}
    		methodResultsField.setValue(newValue);
    	}
    }
    
    private void addCalls(List<? extends Statement> stmts, List<Expression> calls) throws DebugException {
    	for (Statement stmt: stmts) {
    		if (stmt instanceof Expression) {
    			Expression e = (Expression)stmt;
    			IJavaValue value = expressionEvaluator.getValue(e, Collections.<Effect>emptySet());
    			if (value != null && (e instanceof MethodInvocation || e instanceof ClassInstanceCreation || e instanceof SuperMethodInvocation)
    					&& !(value instanceof IJavaPrimitiveValue || value.isNull() || (value instanceof IJavaObject && "Ljava/lang/String;".equals(value.getSignature()))))
    				calls.add((Expression)stmt);
    		} else if (stmt instanceof Block) {
    			addCalls(Arrays.asList(((Block)stmt).statements()), calls);
    		}
    	}
    }
	
	/**
	 * Initializes implementation details.
	 */
	public void init() {
		try {
			implType.sendMessage("init", "()V", new IJavaValue[] { }, thread);
		} catch (DebugException e) {
			boolean result = EclipseUtils.showQuestion("Continue?", "We were unable to install a SecurityManager that prevents our synthesis from having external effects such as deleting files.  Would you still like to continue with the synthesis?");
			if (!result)
				throw new StopSynthesis();
		}
	}
	
	public static class StopSynthesis extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public StopSynthesis() {
			super();
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
			EclipseUtils.showError("Error", "Error", e);
			return false;
		}
	}
	
	/**
	 * Gets the number of statements whose evaluation crashed.
	 * @return The number of statements whose evaluation crashed.
	 */
	public int getNumCrashes() {
		return crashingStatements.size() + skipped;
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
		 * @param disableBreakpoints Whether to disable breakpoints
		 * during evaluation.
		 * @return The result of the evaluation.
		 */
	    public static IEvaluationResult evaluateExpression(ICompiledExpression compiled, IAstEvaluationEngine engine, IJavaStackFrame stack, boolean disableBreakpoints) {
			try {
				engine.evaluateExpression(compiled, stack, evaluationListener, DebugEvent.EVALUATION_IMPLICIT, !disableBreakpoints);
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