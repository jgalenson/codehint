package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIType;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.exprgen.typeconstraint.UnknownConstraint;
import codehint.property.Property;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;


/**
 * Class for generating expressions that can evaluation to a given value.
 */
public final class ExpressionGenerator {
	
	private final static InfixExpression.Operator[] INT_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.LESS, InfixExpression.Operator.LESS_EQUALS, InfixExpression.Operator.GREATER, InfixExpression.Operator.GREATER_EQUALS };
	private final static InfixExpression.Operator[] BOOLEAN_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_OR  };
	private final static InfixExpression.Operator[] REF_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS };

	private final static Set<String> classBlacklist = new HashSet<String>();
	private final static Map<String, Set<String>> methodBlacklist = new HashMap<String, Set<String>>();
	
	/**
	 * Initializes the blacklist.
	 */
	public static void initBlacklist() {
		classBlacklist.add("codehint.CodeHint");
		methodBlacklist.put("java.io.File", new HashSet<String>(Arrays.asList("createNewFile", "delete", "mkdir", "mkdirs", "renameTo", "setLastModified", "setReadOnly")));
	}
	
	/**
	 * Clears the blacklist.
	 */
	public static void clearBlacklist() {
		classBlacklist.clear();
		methodBlacklist.clear();
	}
	
	private final IJavaDebugTarget target;
	private final IJavaStackFrame stack;
	private final SubtypeChecker subtypeChecker;
	private final TypeCache typeCache;
	private final EvaluationManager evalManager;
	private final IJavaReferenceType thisType;
	private final IJavaType intType;
	private final IJavaType booleanType;
	private final IJavaValue zero;
	private final TypedExpression one;
	private final TypedExpression two;
	// Cache the generated expressions
	//private final Map<Pair<TypeConstraint, Integer>, List<TypedExpression>> cachedExprs;
	
	private Map<Value, ArrayList<EvaluatedExpression>> equivalences;
	private TypeConstraint typeConstraint;
	
	public ExpressionGenerator(IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager) {
		this.target = target;
		this.stack = stack;
		this.subtypeChecker = subtypeChecker;
		this.typeCache = typeCache;
		this.evalManager = evalManager;
		try {
			this.thisType = stack.getReferenceType();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.intType = EclipseUtils.getFullyQualifiedType("int", stack, target, typeCache);
		this.booleanType = EclipseUtils.getFullyQualifiedType("boolean", stack, target, typeCache);
		this.zero = target.newValue(0);
		this.one = ExpressionMaker.makeNumber("1", target.newValue(1), intType);
		this.two = ExpressionMaker.makeNumber("2", target.newValue(2), intType);
		//this.cachedExprs = new HashMap<Pair<TypeConstraint, Integer>, List<TypedExpression>>();
	}
	
	/**
	 * Generates all the expressions (up to a certain depth) whose value
	 * in the current stack frame is that of the demonstration.
	 * @param property The property entered by the user.
	 * @param typeConstraint The constraint on the type of the expressions
	 * being generated.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor Progress monitor.
	 * @param maxExprDepth The maximum depth of expressions to search.
	 * @return A list containing strings of all the expressions (up
	 * to the given depth) whose result in the current stack frame satisfies
	 * the given pdspec.
	 */
	public ArrayList<FullyEvaluatedExpression> generateExpression(Property property, TypeConstraint typeConstraint, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor, int maxExprDepth) {
		monitor.beginTask("Expression generation and evaluation", IProgressMonitor.UNKNOWN);
		
		try {
			this.equivalences = new HashMap<Value, ArrayList<EvaluatedExpression>>();
			this.typeConstraint = typeConstraint;
			IJavaValue demonstration = property instanceof ValueProperty ? ((ValueProperty)property).getValue() : null;
	
			long startTime = System.currentTimeMillis();
			
			// TODO: Re-enable cache?  Maybe even cache result of entire method?  How does it interact with expansion?
			//Pair<TypeConstraint, Integer> cacheKey = new Pair<TypeConstraint, Integer>(typeConstraint, maxExprDepth);
			List<TypedExpression> allTypedExprs = genAllExprs(demonstration, 0, maxExprDepth, monitor);
			 
			ArrayList<EvaluatedExpression> evaluatedExprs = new ArrayList<EvaluatedExpression>();
			ArrayList<TypedExpression> unevaluatedExprs = new ArrayList<TypedExpression>();
	    	for (TypedExpression e : allTypedExprs)
	    		if (e.getValue() != null && e instanceof EvaluatedExpression && !"V".equals(e.getValue().getSignature()))
	    			evaluatedExprs.add((EvaluatedExpression)e);
	    		else
	    			unevaluatedExprs.add(e);
	    	
	    	EclipseUtils.log("Generated " + allTypedExprs.size() + " potential expressions, of which " + evaluatedExprs.size() + " already have values and " + unevaluatedExprs.size() + " still need to be evaluated.");

    		SubMonitor evalMonitor = SubMonitor.convert(monitor, "Expression evaluation", allTypedExprs.size());
	    	ArrayList<FullyEvaluatedExpression> results = evalManager.evaluateExpressions(evaluatedExprs, property, synthesisDialog, evalMonitor);
	    	if (unevaluatedExprs.size() > 0) {
	    		ArrayList<FullyEvaluatedExpression> evaled = evalManager.evaluateExpressions(unevaluatedExprs, property, synthesisDialog, evalMonitor);
	    		for (FullyEvaluatedExpression e: evaled)
	    			Utils.addToMap(equivalences, e.getWrapperValue(), e);
	    		results.addAll(evaled);
	    	}
			for (EvaluatedExpression e : results)
				ExpressionMaker.setExpressionValue(e.getExpression(), e.getValue());
			// Expand equivalencies.
	    	ArrayList<EvaluatedExpression> extraResults = expandEquivalences(results);
	    	// Evaluate them.  We use a null pdspec since they are all equivalent to a valid expression, but we might need to get their toString.  This also reports them to the gui.
	    	results.addAll(evalManager.evaluateExpressions(extraResults, null, synthesisDialog, evalMonitor));
			
			EclipseUtils.log("Expression generation found " + results.size() + " valid expressions and took " + (System.currentTimeMillis() - startTime) + " milliseconds.");

			/*for (Map.Entry<Value, ArrayList<EvaluatedExpression>> entry : equivalences.entrySet())
				System.out.println(entry.getKey() + " -> " + entry.getValue().toString());
			int totalNumEquivExprs = 0;
			for (ArrayList<EvaluatedExpression> es: equivalences.values())
				totalNumEquivExprs += es.size();
			System.out.println("Found " + equivalences.size() + " equivalences classes that contain " + totalNumEquivExprs + " expressions.");*/
			
	    	monitor.done();
	    	return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Recursively generates all expressions whose value in the
	 * current stack frame is that of the demonstration.
	 * @param demonstration The value entered by the user.
	 * @param depth The current depth, counting up from 0.
	 * @param maxDepth The maximum depth to search (inclusive).
	 * @param monitor Progress monitor.
	 * @return all expressions whose result in the
	 * current stack frame satisfies the current pdspec.
	 */
	private List<TypedExpression> genAllExprs(IJavaValue demonstration, int depth, int maxDepth, IProgressMonitor monitor) {
		if (depth > maxDepth)
			return new ArrayList<TypedExpression>(0);
		try {
    		List<TypedExpression> nextLevel = genAllExprs(demonstration, depth + 1, maxDepth, monitor);
    		List<TypedExpression> curLevel = new ArrayList<TypedExpression>();
			Set<IJavaReferenceType> objectInterfaceTypes = new HashSet<IJavaReferenceType>();
			IJavaType[] constraintTypes = typeConstraint.getTypes(stack, target, typeCache);
    		
    		// Get constants (but only at the top-level).
			// We add these directly to curLevel and not equivalences because we don't want to substitute them anywhere else.
    		if (depth == 0 && demonstration != null && ExpressionMaker.isInt(demonstration.getJavaType()) && !"0".equals(demonstration.toString()))
    			curLevel.add(ExpressionMaker.makeNumber(demonstration.toString(), target.newValue(Integer.parseInt(demonstration.toString())), intType));
    		if (depth == 0 && demonstration != null && ExpressionMaker.isBoolean(demonstration.getJavaType()))
    			curLevel.add(ExpressionMaker.makeBoolean(Boolean.parseBoolean(demonstration.toString()), target.newValue(Boolean.parseBoolean(demonstration.toString())), booleanType));
    		// Add calls to the desired type's constructors (but only at the top-level).
    		if (depth == 0)
    			for (IJavaType type: constraintTypes)
    				if (type instanceof IJavaClassType)
    					addMethodCalls(new TypedExpression(null, type), nextLevel, curLevel, depth, maxDepth);
    		// Add zero and null (but only at the bottom level)
    		if (depth == maxDepth) {
    			boolean hasInt = false;
    			boolean hasObject = false;
    			for (IJavaType type: constraintTypes) {
    				if (ExpressionMaker.isInt(type))
    					hasInt = true;
    				else if (EclipseUtils.isObject(type))
    					hasObject = true;
    			}
    			if (depth > 0 || hasInt)
    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeNumber("0", zero, intType), depth, maxDepth);
    			if (depth > 0 || (hasObject && !(typeConstraint instanceof MethodConstraint) && !(typeConstraint instanceof FieldConstraint)))  // If we have a method or field constraint, we can't have null.
    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeNull(target), depth, maxDepth);
    		}
    		
    		// Copy over the stuff from the next level.
    		for (TypedExpression e : nextLevel)
    			if (depth > 0 || isHelpfulType(e.getType(), depth))  // Note that this relies on the fact that something helpful for depth>=2 will be helpful for depth>=1.  If this changes, we'll need to call it again.
    				curLevel.add(e);
    		
    		if (nextLevel.isEmpty()) {
	    		// Get variables of helpful types.
				IJavaVariable[] locals = stack.getLocalVariables();
				for (IJavaVariable l : locals) {
					IJavaType lType = EclipseUtils.getTypeOfVariableAndLoadIfNeeded(l, stack);
					if (isHelpfulType(lType, depth))
						addUniqueExpressionToList(curLevel, ExpressionMaker.makeVar(l.getName(), (IJavaValue)l.getValue(), lType, false), depth, maxDepth);
				}
				// Add "this" if we're not in a static context.
				if (isHelpfulType(thisType, depth)
						&& !stack.isStatic())
					addUniqueExpressionToList(curLevel, ExpressionMaker.makeThis(stack.getThis(), thisType), depth, maxDepth);
    		} else {
				// Get binary ops.
				// We use string comparisons to avoid duplicates, e.g., x+y and y+x.
	    		for (TypedExpression l : nextLevel) {
	    			if (monitor.isCanceled())
	    				throw new OperationCanceledException();
	        		for (TypedExpression r : nextLevel) {
	        			// Help ensure that we generate infix operations for equivalent things (e.g., y+z if x=y=z; without this we would not generate x+x).
	        			if (l == r && l.getWrapperValue() != null) {
		        			ArrayList<EvaluatedExpression> lEquivs = equivalences.get(l.getWrapperValue());
		        			if (lEquivs != null) {
		        				for (TypedExpression equiv: lEquivs) {
		        					if (equiv != r) {
		        						r = equiv;
		        						break;
		        					}
		        				}
		        			}
	        			}
	        			// Arithmetic operations, e.g., +,*.
						if (ExpressionMaker.isInt(l.getType()) && ExpressionMaker.isInt(r.getType()) && isHelpfulType(intType, depth)
								&& l.getExpression().getProperty("isConstant") == null && r.getExpression().getProperty("isConstant") == null) {
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.PLUS, r, intType), depth, maxDepth);
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) <= 0)
								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.TIMES, r, intType), depth, maxDepth);
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) != 0)
								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.MINUS, r, intType), depth, maxDepth);
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) != 0
									&& (r.getValue() == null || !r.getValue().getValueString().equals("0")))  // Don't divide by things we know are 0.
								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.DIVIDE, r, intType), depth, maxDepth);
						}
						// Integer comparisons, e.g., ==,<.
						if (isHelpfulType(booleanType, depth) && ExpressionMaker.isInt(l.getType()) && ExpressionMaker.isInt(r.getType()))
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0
									&& (!(l.getExpression() instanceof PrefixExpression) || !(r.getExpression() instanceof PrefixExpression)))
								for (InfixExpression.Operator op : INT_COMPARE_OPS)
									addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, op, r, booleanType), depth, maxDepth);
						// Boolean connectives, &&,||.
						if (isHelpfulType(booleanType, depth) && ExpressionMaker.isBoolean(l.getType()) && ExpressionMaker.isBoolean(r.getType()))
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
								for (InfixExpression.Operator op : BOOLEAN_COMPARE_OPS)
									addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, op, r, booleanType), depth, maxDepth);
						// Array access, a[i].
						if (l.getType() instanceof IJavaArrayType && ExpressionMaker.isInt(r.getType())) {
							IJavaType elemType = ExpressionMaker.getArrayElementType(l);
							if (elemType != null && isHelpfulType(ExpressionMaker.getArrayElementType(l), depth)) {
								// Get the value if we can and skip things with null arrays or out-of-bounds indices.
								boolean isNull = false;
								IJavaValue value = null;
								if (r.getValue() != null) {
									int index = Integer.parseInt(r.getValue().getValueString());
									if (index < 0)
										isNull = true;
									else if (l.getValue() != null) {
										if (l.getValue().isNull())
											isNull = true;
										else {
											IJavaArray array = (IJavaArray)l.getValue();
											if (array.isNull() || index >= array.getLength())
												isNull = true;
											else
												value = array.getValue(index);
										}
									}
								}
								if (!isNull)
									addUniqueExpressionToList(curLevel, ExpressionMaker.makeArrayAccess(l, r, value), depth, maxDepth);
							}
						}
						// Object/array comparisons
						if (l.getType() instanceof IJavaReferenceType && r.getType() instanceof IJavaReferenceType
								&& isHelpfulType(booleanType, depth)
								&& (subtypeChecker.isSubtypeOf(l.getType(), r.getType()) || subtypeChecker.isSubtypeOf(r.getType(), l.getType())))
							if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
								for (InfixExpression.Operator op : REF_COMPARE_OPS)
									addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, op, r, booleanType), depth, maxDepth);
							
	        		}
	    		}
	    		// Get unary ops
	    		for (TypedExpression e : nextLevel) {
	    			// Arithmetic with constants.
	    			if (ExpressionMaker.isInt(e.getType()) && isHelpfulType(intType, depth)
	    					&& e.getExpression().getProperty("isConstant") == null) {
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.PLUS, one, intType), depth, maxDepth);
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.TIMES, two, intType), depth, maxDepth);
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.MINUS, one, intType), depth, maxDepth);
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.DIVIDE, two, intType), depth, maxDepth);
	    			}
	    			// Field accesses to non-static fields from non-static scope.
	    			if (e.getType() instanceof IJavaClassType
	    					&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
	    				addFieldAccesses(e, curLevel, depth, maxDepth);
	    			// Boolean negation.
	    			if (ExpressionMaker.isBoolean(e.getType()) && isHelpfulType(booleanType, depth)
	    					&& !(e.getExpression() instanceof PrefixExpression) && !(e.getExpression() instanceof InfixExpression)
	    					&& e.getExpression().getProperty("isConstant") == null)  // Disallow things like !(x < y) and !(!x).
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makePrefix(target, e, PrefixExpression.Operator.NOT, booleanType), depth, maxDepth);
	    			// Integer negation.
	    			if (ExpressionMaker.isInt(e.getType()) && isHelpfulType(intType, depth)
	    					&& !(e.getExpression() instanceof PrefixExpression) && !(e.getExpression() instanceof InfixExpression)
	    					&& e.getExpression().getProperty("isConstant") == null)  // Disallow things like -(-x) and -(x + y).
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makePrefix(target, e, PrefixExpression.Operator.MINUS, intType), depth, maxDepth);
	    			// Array length (which uses the field access AST).
	    			if (e.getType() instanceof IJavaArrayType && isHelpfulType(intType, depth)
	    					&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
	    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeFieldAccess(e, "length", intType, e.getValue() != null ? target.newValue(((IJavaArray)e.getValue()).getLength()) : null), depth, maxDepth);
	    			// Method calls to non-static methods from non-static scope.
	    			if (ExpressionMaker.isObjectOrInterface(e.getType())
	    					&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
	    				addMethodCalls(e, nextLevel, curLevel, depth, maxDepth);
	    			// Collect the class and interface types we've seen.
	    			if (ExpressionMaker.isObjectOrInterface(e.getType()))
	    				objectInterfaceTypes.add((IJavaReferenceType)e.getType());
	    		}
	    		// Extra things
	    		{
	    			IImportDeclaration[] imports = ((ICompilationUnit)EclipseUtils.getProject(stack).findElement(new Path(stack.getSourcePath()))).getImports();
	    			Set<String> importsSet = new HashSet<String>(imports.length);
	    			for (IImportDeclaration imp : imports)
	    				importsSet.add(imp.getElementName());
	    			// Field accesses from static scope.
	    			if (stack.isStatic() && !stack.getReceivingTypeName().contains("<"))  // TODO: Allow referring to generic classes (and below).
	    				addFieldAccesses(ExpressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType), curLevel, depth, maxDepth);
	    			// Method calls from static scope.
	    			if (stack.isStatic() && !stack.getReceivingTypeName().contains("<"))
	    				addMethodCalls(ExpressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType), nextLevel, curLevel, depth, maxDepth);
	    			// Accesses/calls to static fields/methods.
	    			for (IJavaReferenceType type : objectInterfaceTypes) {
	    				String typeName = type.getName();
	    				// If we have imported the type or it is an inner class of the this type, use the unqualified typename for brevity.
	    				if (importsSet.contains(typeName) || (typeName.contains("$") && thisType.getName().equals(typeName.substring(0, typeName.lastIndexOf('$')))))
	    					typeName = EclipseUtils.getUnqualifiedName(EclipseUtils.sanitizeTypename(typeName));
	    				addFieldAccesses(ExpressionMaker.makeStaticName(typeName, type), curLevel, depth, maxDepth);
	    				addMethodCalls(ExpressionMaker.makeStaticName(typeName, type), nextLevel, curLevel, depth, maxDepth);
	    			}
	    			// Calls to static methods and fields of imported classes.
					for (IImportDeclaration imp : imports) {
						String fullName = imp.getElementName();
						String shortName = EclipseUtils.getUnqualifiedName(fullName);  // Use the unqualified typename for brevity.
						if (!imp.isOnDemand()) {  // TODO: What should we do with import *s?  It might be too expensive to try all static methods.  This ignores them.
							IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(fullName, stack, target, typeCache);
							if (importedType != null) {
								if (!objectInterfaceTypes.contains(importedType)) {  // We've already handled these above.
									addFieldAccesses(ExpressionMaker.makeStaticName(shortName, importedType), curLevel, depth, maxDepth);
									addMethodCalls(ExpressionMaker.makeStaticName(shortName, importedType), nextLevel, curLevel, depth, maxDepth);
								}
							} else
								;//System.err.println("I cannot get the class of the import " + fullName);
						}
					}
	    		}
    		}
    		/*System.out.println("Exploring " + result.size() + " possible expressions.");
    		for (TypedExpression e : result)
    			System.out.println(e.toString());*/
    		return curLevel;
		} catch (DebugException e) {
			e.printStackTrace();
        	EclipseUtils.showError("Error", "An error occurred during expression generation.", e);
			throw new RuntimeException("I cannot compute all valid expressions.");
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
		
	}
	
	// TODO: Convert field/method code to use the public API?  I can use IType to get fields/methods (but they only get declared ones, so I'd have to walk the supertype chain), IType to get their signature, Signature.getSignature{Qualifier,SimpleName} to get type names, and then EclipseUtils.getType-like code to get the IType back.
	
	/**
	 * Gets all the visible fields of the given type.
	 * @param type The type whose fields we want to get.
	 * @return All of the visible fields of the given type.
	 */
	public static List<Field> getFields(IJavaType type) {
		if (type != null && EclipseUtils.isObject(type)) {
			List<?> untypedFields = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleFields();
			ArrayList<Field> fields = new ArrayList<Field>(untypedFields.size());
			for (Object o: untypedFields)
				fields.add((Field)o);
			return fields;
		} else
			return new ArrayList<Field>(0);
	}
	
	/**
	 * Adds field accesses of the given expression.
	 * @param e The receiver expression.
	 * @param ops The list into which we will insert
	 * the newly-generated expressions.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @throws DebugException
	 */
	private void addFieldAccesses(TypedExpression e, List<TypedExpression> ops, int depth, int maxDepth) throws DebugException {
		// We could use the public Eclipse API here, but it isn't as clean and works on objects not types, so wouldn't work with our static accesses, which we give a null value.  Note that as below with methods, we must now be careful converting between jdi types and Eclipse types. 
		IJavaObject obj = e.getValue() != null ? (IJavaObject)e.getValue() : null;
		Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		Type thisTypeImpl = ((JDIType)thisType).getUnderlyingType();
		boolean isStatic = ExpressionMaker.isStatic(e.getExpression());
		String objTypeName = isStatic ? e.getExpression().toString() : objTypeImpl.name();
		for (Field field: getFields(e.getType())) {
			if ((!field.isPublic() && !field.declaringType().equals(thisTypeImpl)) || (isStatic != field.isStatic()) || field.isSynthetic())
				continue;
			IJavaType fieldType = EclipseUtils.getTypeAndLoadIfNeeded(field.typeName(), stack, target, typeCache);
			/*if (fieldType == null)
				System.err.println("I cannot get the class of " + objTypeImpl.name() + "." + field.name() + "(" + field.typeName() + ")");*/
			if (fieldType != null && isHelpfulType(fieldType, depth)) {
				TypedExpression receiver = e;
				if (e.getExpression() instanceof ThisExpression || e.getType().equals(thisType))
					receiver = null;  // Don't use a receiver if it is null or the this type.
				else if (field.isStatic())
					receiver = ExpressionMaker.makeStaticName(EclipseUtils.sanitizeTypename(objTypeName), (IJavaReferenceType)e.getType());
				IJavaValue fieldValue = null;
				if (field.isStatic())
					fieldValue = (IJavaValue)((IJavaReferenceType)e.getType()).getField(field.name()).getValue();
				else if (obj != null)
					fieldValue = (IJavaValue)obj.getField(field.name(), !field.declaringType().equals(objTypeImpl)).getValue();
				TypedExpression fieldExpr = receiver == null ? ExpressionMaker.makeVar(field.name(), fieldValue, fieldType, true) : ExpressionMaker.makeFieldAccess(receiver, field.name(), fieldType, fieldValue); 
				addUniqueExpressionToList(ops, fieldExpr, depth, maxDepth);
			}
		}
	}

	/**
	 * Gets all the visible methods of the given type.
	 * @param type The type whose methods we want to get.
	 * @return All of the visible methods of the given type.
	 */
	public static List<Method> getMethods(IJavaType type) {
		if (type != null && EclipseUtils.isObject(type)) {
			List<?> untypedMethods = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleMethods();
			ArrayList<Method> methods = new ArrayList<Method>(untypedMethods.size());
			for (Object o: untypedMethods)
				methods.add((Method)o);
			return methods;
		} else
			return new ArrayList<Method>(0);
	}
	
	/**
	 * Checks whether the given method can legally be called.
	 * @param method The method to check.
	 * @param thisType The type of the this object.
	 * @param isConstructor Whether we are expecting a constructor.
	 * @return Whether the given method is legal to call.
	 */
	public static boolean isLegalMethod(Method method, IJavaType thisType, boolean isConstructor) {
		return ((method.isPublic() || method.declaringType().equals(((JDIType)thisType).getUnderlyingType())) && (!method.isConstructor() || !method.isPackagePrivate()))  // Constructors are not marked as public.
				&& isConstructor == method.isConstructor() && !method.isSynthetic() && !method.isStaticInitializer() && !method.declaringType().name().equals("java.lang.Object");
	}

	/**
	 * Adds method calls of the given expression.
	 * @param e The receiver expression.
	 * @param nextLevel The expressions to use as
	 * arguments.
	 * @param ops The list into which we will insert
	 * the newly-generated expressions.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @throws DebugException
	 */
	private void addMethodCalls(TypedExpression e, List<TypedExpression> nextLevel, List<TypedExpression> ops, int depth, int maxDepth) throws DebugException {
		// The public API doesn't tell us the methods of a class, so we need to use the jdi.  Note that we must now be careful converting between jdi types and Eclipse types.
		Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		if (classBlacklist.contains(objTypeImpl.name()))
			return;
		boolean isConstructor = e.getExpression() == null;
		boolean isStatic = !isConstructor && ExpressionMaker.isStatic(e.getExpression());
		String objTypeName = isStatic ? e.getExpression().toString() : objTypeImpl.name();
		Method stackMethod = ((JDIStackFrame)stack).getUnderlyingMethod();
		// This code is so much nicer in a functional language.</complain>
		List<Method> visibleMethods = getMethods(e.getType());
		List<Method> legalMethods = new ArrayList<Method>(visibleMethods.size());
		for (Method method: visibleMethods) {
			if (methodBlacklist.containsKey(objTypeName) && methodBlacklist.get(objTypeName).contains(method.name()))
				continue;
			legalMethods.add(method);
		}
		OverloadChecker overloadChecker = new OverloadChecker(e.getType());
		for (Method method : legalMethods) {
			// Filter out java.lang.Object methods and fake methods like "<init>".  Note that if we don't filter out Object's methods we do getClass() and then call reflective methods, which is bad times.
			// TODO: Allow calling protected and package-private things when it's legal.
			if (!isLegalMethod(method, thisType, isConstructor) || (isStatic != method.isStatic()) || method.equals(stackMethod))  // Disable explicit recursion (that is, calling the current method), since it is definitely not yet complete.
				continue;
			if (!isConstructor && method.returnTypeName().equals("void"))
				continue;
			IJavaType returnType = isConstructor ? e.getType() : EclipseUtils.getTypeAndLoadIfNeeded(method.returnTypeName(), stack, target, typeCache);
			/*if (returnType == null)
				System.err.println("I cannot get the class of the return type of " + objTypeImpl.name() + "." + method.name() + "() (" + method.returnTypeName() + ")");*/
			if (returnType != null && (isHelpfulType(returnType, depth) || method.isConstructor())) {  // Constructors have void type... 
				List<?> argumentTypeNames = method.argumentTypeNames();
				// TODO: Improve overloading detection.
				overloadChecker.setMethod(method);
				ArrayList<ArrayList<TypedExpression>> allPossibleActuals = new ArrayList<ArrayList<TypedExpression>>(argumentTypeNames.size());
				Iterator<?> aIt = argumentTypeNames.iterator();
				while (aIt.hasNext()) {
					IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)aIt.next(), stack, target, typeCache);
					if (argType == null) {
						//System.err.println("I cannot get the class of the arguments to " + objTypeImpl.name() + "." + method.name() + "()");
						break;
					}
					ArrayList<TypedExpression> curPossibleActuals = new ArrayList<TypedExpression>();
					// TODO (low priority): This can get called multiple times if there are multiple args with the same type (or even different methods with args of the same type), but this has a tiny effect compared to the general state space explosion problem.
					for (TypedExpression a : nextLevel)
						if ((new SupertypeBound(argType)).isFulfilledBy(a.getType(), subtypeChecker, typeCache, stack, target)) {  // TODO: This doesn't work for generic methods.
							if (overloadChecker.needsCast(argType, a.getType(), allPossibleActuals.size()))  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
								a = ExpressionMaker.makeCast(a, argType, a.getValue());
							curPossibleActuals.add(a);
						}
					allPossibleActuals.add(curPossibleActuals);
				}
				if (allPossibleActuals.size() == argumentTypeNames.size()) {
					TypedExpression receiver = e;
					if (method.isStatic())
						receiver = ExpressionMaker.makeStaticName(EclipseUtils.sanitizeTypename(objTypeName), (IJavaReferenceType)e.getType());
					makeAllCalls(method, method.name(), receiver, returnType, ops, allPossibleActuals, new ArrayList<TypedExpression>(allPossibleActuals.size()), depth, maxDepth);
				}
			}
		}
	}
	
	/**
	 * Adds the given expression to the given list
	 * if it has the right depth and if it is unique
	 * wrt UniqueASTChecker.
	 * We need to check the depth since genAllExprs
	 * returns is cumulative, so when the max depth is 2,
	 * at depth 0 nextLevel will be a superset of the
	 * nextLevel at depth 1 and so we will generate the same
	 * expressions again.
	 * @param list List to which to add unique expressions.
	 * @param e Expression to add if it is unique.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 */
	private void addUniqueExpressionToList(List<TypedExpression> list, TypedExpression e, int depth, int maxDepth) {
		if (e != null && isUnique(e)) {
			if (e.getWrapperValue() != null && equivalences.containsKey(e.getWrapperValue()))
				equivalences.get(e.getWrapperValue()).add((EvaluatedExpression)e);
			else {
				if (e.getValue() != null)
					equivalences.put(e.getWrapperValue(), Utils.makeList((EvaluatedExpression)e));
				if (getDepth(e) == (maxDepth - depth))
					list.add(e);
			}
		}
	}
	
	/**
	 * Determines whether an expression of the given type can be
	 * useful to us.
	 * We currently assume that anything can be useful until the
	 * outermost depth, since there are probably methods that
	 * can take in anything, but that only the desired type
	 * is helpful otherwise.
	 * TODO (lowish priority): We could be smarter here, e.g.,
	 * by pruning ints if the target is not an int and there
	 * are no methods with int arguments and no arrays.
	 * @param curType The type to test.
	 * @param depth The current depth.
	 * @return Whether an expression of the given type can be useful to us.
	 * @throws DebugException 
	 */
	private boolean isHelpfulType(IJavaType curType, int depth) throws DebugException {
		if (curType != null && "V".equals(curType.getSignature()))  // Void things never return anything useful.
			return false;
		// TODO: The commented parts in {DesiredType,SupertypeBound}.fulfillsConstraint disallow downcasting things, e.g., (Foo)x.getObject(), which could be legal, but is unlikely to be.
		if (depth > 0)
			return true;
		return typeConstraint.isFulfilledBy(curType, subtypeChecker, typeCache, stack, target);
	}
	
	/**
	 * Creates all possible calls using the given actuals.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param receiver The receiving object.
	 * @param returnType The return type of the function.
	 * @param ops The list to add the unique calls created. 
	 * @param possibleActuals A list of all the possible actuals for each argument.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @throws DebugException 
	 */
	private void makeAllCalls(Method method, String name, TypedExpression receiver, IJavaType returnType, List<TypedExpression> ops, ArrayList<ArrayList<TypedExpression>> possibleActuals, ArrayList<TypedExpression> curActuals, int depth, int maxDepth) {
		if (curActuals.size() == possibleActuals.size())
			addUniqueExpressionToList(ops, ExpressionMaker.makeCall(name, receiver, curActuals, returnType, thisType, method, target), depth, maxDepth);
		else {
			int argNum = curActuals.size();
			for (TypedExpression e : possibleActuals.get(argNum)) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, returnType, ops, possibleActuals, curActuals, depth, maxDepth);
				curActuals.remove(argNum);
			}
		}
	}
	
	/**
	 * Creates all possible calls/creations using the given actuals.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param receiver The receiving object for method calls or a type
	 * literal representing the type being created for creations.
	 * @param result The list to add the unique calls created. 
	 * @param possibleActuals A list of all the possible actuals for each argument.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 */
	private static void makeAllCalls(Method method, String name, Expression receiver, List<Expression> result, ArrayList<Iterable<TypedExpression>> possibleActuals, ArrayList<Expression> curActuals) {
		if (curActuals.size() == possibleActuals.size()) {
			if ("<init>".equals(name))
				result.add(ExpressionMaker.makeClassInstanceCreation(((TypeLiteral)receiver).getType(), curActuals, method));
			else
				result.add(ExpressionMaker.makeCall(name, receiver, curActuals, method));
		} else {
			int depth = curActuals.size();
			for (TypedExpression e : possibleActuals.get(depth)) {
				curActuals.add(e.getExpression());
				makeAllCalls(method, name, receiver, result, possibleActuals, curActuals);
				curActuals.remove(depth);
			}
		}
	}
	
	/**
	 * Finds other expressions equivalent to the given ones.
	 * @param exprs Expressions for which we want to find
	 * equivalent expressions.
	 * @return Expressions that are equivalent to the given
	 * ones.  Note that the result is disjoint from the input.
	 * @throws DebugException
	 */
	private ArrayList<EvaluatedExpression> expandEquivalences(ArrayList<FullyEvaluatedExpression> exprs) throws DebugException {
		Set<Value> values = new HashSet<Value>();
		Set<EvaluatedExpression> newlyExpanded = new HashSet<EvaluatedExpression>();
		for (EvaluatedExpression result: exprs) {
			Value value = result.getWrapperValue();
			if (!equivalences.containsKey(value))  // This can happen for demonstrated primitive values, since those are used but not put into the equivalences map. 
				equivalences.put(value, Utils.makeList(result));
			values.add(value);
		}
		ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
		for (Value value: values) {
			for (TypedExpression expr : new ArrayList<TypedExpression>(equivalences.get(value)))  // Make a copy since we'll probably add expressions to this.
				expandEquivalencesRec(expr.getExpression(), newlyExpanded);
			for (EvaluatedExpression expr: getEquivalentExpressions(value, null, typeConstraint))
				if (typeConstraint.isFulfilledBy(expr.getType(), subtypeChecker, typeCache, stack, target))
					results.add(expr);
		}
		results.removeAll(exprs);
		return results;
	}
	
	/**
	 * Finds expressions that are equivalent to the given one.
	 * @param expr The given expression.
	 * @param newlyExpanded The set of expressions we have already
	 * expanded to stop us from doing extra work or recursing infinitely.
	 * @throws DebugException
	 */
	private void expandEquivalencesRec(Expression expr, Set<EvaluatedExpression> newlyExpanded) throws DebugException {
		if (expr == null)
			return;
		IJavaValue javaValue = ExpressionMaker.getExpressionValue(expr);
		if (javaValue == null)
			return;
		Value value = new Value(javaValue);
		IJavaType type = javaValue.getJavaType();
		EvaluatedExpression valued = new EvaluatedExpression(expr, type, value);
		if (newlyExpanded.contains(valued))
			return;
		newlyExpanded.add(valued);
		int curDepth = getDepth(expr);
		if (!equivalences.containsKey(value))
			equivalences.put(value, Utils.makeList(valued));
		ArrayList<EvaluatedExpression> curEquivalences = equivalences.get(value);
		if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ParenthesizedExpression || expr instanceof ThisExpression || expr instanceof NullLiteral) {
			// Do nothing as there's nothing to expand.
		} else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			expandEquivalencesRec(infix.getLeftOperand(), newlyExpanded);
			expandEquivalencesRec(infix.getRightOperand(), newlyExpanded);
			for (TypedExpression l : getEquivalentExpressions(infix.getLeftOperand(), UnknownConstraint.getUnknownConstraint()))
				for (TypedExpression r : getEquivalentExpressions(infix.getRightOperand(), UnknownConstraint.getUnknownConstraint()))
					if (getDepth(l.getExpression()) < curDepth && getDepth(r.getExpression()) < curDepth && isUsefulInfix(l, infix.getOperator(), r))
						addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeInfix(l.getExpression(), infix.getOperator(), r.getExpression()), type, value), valued);
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			expandEquivalencesRec(array.getArray(), newlyExpanded);
			expandEquivalencesRec(array.getIndex(), newlyExpanded);
			for (TypedExpression a : getEquivalentExpressions(array.getArray(), UnknownConstraint.getUnknownConstraint()))
				for (TypedExpression i : getEquivalentExpressions(array.getIndex(), UnknownConstraint.getUnknownConstraint()))
					if (getDepth(a.getExpression()) < curDepth && getDepth(i.getExpression()) < curDepth)
						addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeArrayAccess(a.getExpression(), i.getExpression()), type, value), valued);
		} else if (expr instanceof FieldAccess) {
			FieldAccess field = (FieldAccess)expr;
			expandEquivalencesRec(field.getExpression(), newlyExpanded);
			for (Expression e : getEquivalentExpressionsOrGiven(field.getExpression(), new FieldConstraint(field.getName().getIdentifier(), UnknownConstraint.getUnknownConstraint())))
				if (getDepth(e) < curDepth)
					addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeFieldAccess(e, field.getName().getIdentifier()), type, value), valued);
		} else if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)expr;
			expandEquivalencesRec(prefix.getOperand(), newlyExpanded);
			for (TypedExpression e : getEquivalentExpressions(prefix.getOperand(), UnknownConstraint.getUnknownConstraint()))
				if (getDepth(e.getExpression()) < curDepth)
					addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makePrefix(e.getExpression(), prefix.getOperator()), type, value), valued);
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			expandCall(call.getExpression(), ExpressionMaker.getMethod(call), call.arguments(), newlyExpanded, value, type, valued, curDepth, curEquivalences);
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			expandCall(ExpressionMaker.makeTypeLiteral(call.getType()), ExpressionMaker.getMethod(call), call.arguments(), newlyExpanded, value, type, valued, curDepth, curEquivalences);
		} else if (expr instanceof CastExpression) {
			CastExpression cast = (CastExpression)expr;
			ExpressionMaker.setExpressionValue(cast.getExpression(), javaValue);
			expandEquivalencesRec(cast.getExpression(), newlyExpanded);
			IJavaType castType = EclipseUtils.getType(cast.getType().toString(), stack, target, typeCache);
			for (TypedExpression e : getEquivalentExpressions(cast.getExpression(), UnknownConstraint.getUnknownConstraint()))
				if (getDepth(e.getExpression()) < curDepth)
					addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeCast(e.getExpression(), castType), type, value), valued);
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
	}
	
	/**
	 * Adds the new expression to the given list if it is not
	 * equal to the current expression.
	 * @param curEquivalences The list.
	 * @param newExpr The new expression.
	 * @param curExpr The old expression.
	 */
	private static void addIfNew(ArrayList<EvaluatedExpression> curEquivalences, EvaluatedExpression newExpr, EvaluatedExpression curExpr) {
		if (!newExpr.equals(curExpr))
			curEquivalences.add(newExpr);
	}

	/**
	 * Returns expressions that are equivalent to the given expression.
	 * If the expression is null or we do not know its value (and thus
	 * is not in our equivalences map), we simply return it.
	 * @param expr The expression for which we want to find equivalent
	 * expressions.
	 * @param constraint The constraint that should hold for the expressions.
	 * @return Expressions that are equivalent to the given expression,
	 * or itself if it is not in our equivalences map.
	 * @throws DebugException
	 */
	private Expression[] getEquivalentExpressionsOrGiven(Expression expr, TypeConstraint constraint) throws DebugException {
		if (expr == null || ExpressionMaker.getExpressionValue(expr) == null)
			return new Expression[] { expr };
		else {
			List<EvaluatedExpression> allPossibleExpressions = getEquivalentExpressions(expr, constraint);
			Expression[] result = new Expression[allPossibleExpressions.size()];
			for (int i = 0; i < result.length; i++)
				result[i] = allPossibleExpressions.get(i).getExpression();
			return result;
		}
	}

	/**
	 * Returns expressions that are equivalent to the given expression
	 * by looking them up in the equivalences map.
	 * @param expr The expression for which we want to find equivalent
	 * expressions.
	 * @param constraint The constraint that should hold for the expressions.
	 * @return Expressions that are equivalent to the given expression.
	 * @throws DebugException
	 */
	private ArrayList<EvaluatedExpression> getEquivalentExpressions(Expression expr, TypeConstraint constraint) throws DebugException {
		IJavaValue value = ExpressionMaker.getExpressionValue(expr);
		return getEquivalentExpressions(new Value(value), expr, constraint);
	}

	/**
	 * Returns expressions that have the given value
	 * by looking them up in the equivalences map.
	 * It also adds the current expression if it is a non-zero constant
	 * and hence not in the equivalences map.
	 * @param curValue The value for which we want to find expressions
	 * with that value.
	 * @param curExpr The current expression.
	 * @param constraint The constraint that should hold for the expressions.
	 * @return Expressions that have the given expression.
	 * @throws DebugException
	 */
	private ArrayList<EvaluatedExpression> getEquivalentExpressions(Value curValue, Expression curExpr, TypeConstraint constraint) throws DebugException {
		ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
		for (EvaluatedExpression expr: equivalences.get(curValue))
			// We might get things that are equivalent but with difference static types (e.g., Object and String when we want a String), so we ensure we satisfy the type constraint.
			// However, we have to special case static accesses/calls (e.g., Foo.bar), as the expression part has type Class not the desired type (Foo).
			if (constraint.isFulfilledBy(expr.getType(), subtypeChecker, typeCache, stack, target) || (ExpressionMaker.isStatic(expr.getExpression()) && (constraint instanceof FieldConstraint || constraint instanceof MethodConstraint)))
				results.add(expr);
		// 0 is already in the equivalences map, but no other int constants are.
		if ((curExpr instanceof NumberLiteral && !"0".equals(((NumberLiteral)curExpr).getToken())) || curExpr instanceof StringLiteral || curExpr instanceof BooleanLiteral)
			results.add(new EvaluatedExpression(curExpr, curValue.getValue().getJavaType(), curValue));
		return results;
	}
	
	/**
	 * Ensures that the given infix operation is useful with respect
	 * to our heuristics that remove uninteresting expressions like
	 * x+0 or x+x.
	 * @param l The left side.
	 * @param op The operation.
	 * @param r The right side.
	 * @return Whether or not the given infix expression is useful.
	 * @throws DebugException
	 */
	private static boolean isUsefulInfix(TypedExpression l, InfixExpression.Operator op, TypedExpression r) throws DebugException {
		if (op == InfixExpression.Operator.PLUS)
			return l.getExpression().getProperty("isConstant") == null && (r.getExpression().getProperty("isConstant") != null || l.getExpression().toString().compareTo(r.getExpression().toString()) < 0);
		else if (op == InfixExpression.Operator.TIMES)
			return l.getExpression().getProperty("isConstant") == null && (r.getExpression().getProperty("isConstant") != null || l.getExpression().toString().compareTo(r.getExpression().toString()) <= 0);
		else if (op == InfixExpression.Operator.MINUS)
			return l.getExpression().getProperty("isConstant") == null && (r.getExpression().getProperty("isConstant") != null || l.getExpression().toString().compareTo(r.getExpression().toString()) != 0);
		else if (op == InfixExpression.Operator.DIVIDE)
			return l.getExpression().getProperty("isConstant") == null && (r.getExpression().getProperty("isConstant") != null || l.getExpression().toString().compareTo(r.getExpression().toString()) != 0);
		else if (ExpressionMaker.isInt(l.getType()) && ExpressionMaker.isInt(r.getType()))
			return l.getExpression().toString().compareTo(r.getExpression().toString()) < 0 && (!(l.getExpression() instanceof PrefixExpression) || !(r.getExpression() instanceof PrefixExpression));
		else
			return l.getExpression().toString().compareTo(r.getExpression().toString()) < 0;
	}

	/**
	 * Finds calls that are equivalent to the given one and
	 * adds them to curEquivalences.
	 * @param expression The expression part of the call.
	 * @param method The method being called.
	 * @param arguments The arguments.
	 * @param newlyExpanded The set of expressions we have already
	 * expanded to stop us from doing extra work or recursing infinitely.
	 * @param value The result of the call.
	 * @param type The type of the result of the call.
	 * @param valued The call itself.
	 * @param curDepth The depth of the call.
	 * @param curEquivalences The set of expressions equivalent
	 * to this call.  The new expressions will be added to this set.
	 * @throws DebugException
	 */
	private void expandCall(Expression expression, Method method, List<?> arguments, Set<EvaluatedExpression> newlyExpanded, Value value, IJavaType type, EvaluatedExpression valued, int curDepth, ArrayList<EvaluatedExpression> curEquivalences) throws DebugException {
		String name = method.name();
		expandEquivalencesRec(expression, newlyExpanded);
		OverloadChecker overloadChecker = new OverloadChecker(EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache));
		overloadChecker.setMethod(method);
		ArrayList<Iterable<TypedExpression>> newArguments = new ArrayList<Iterable<TypedExpression>>(arguments.size());
		ArrayList<TypeConstraint> argConstraints = new ArrayList<TypeConstraint>(arguments.size());
		for (int i = 0; i < arguments.size(); i++) {
			Expression curArg = (Expression)arguments.get(i);
			expandEquivalencesRec(curArg, newlyExpanded);
			ArrayList<TypedExpression> allCurArgPossibilities = new ArrayList<TypedExpression>();
			IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache);
			TypeConstraint argConstraint = new SupertypeBound(argType);
			for (TypedExpression arg : getEquivalentExpressions(curArg, argConstraint))
				if (getDepth(arg.getExpression()) < curDepth) {
					if (overloadChecker.needsCast(argType, arg.getType(), newArguments.size()))  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
						arg = ExpressionMaker.makeCast(arg, argType, arg.getValue());
					allCurArgPossibilities.add(arg);
				}
			newArguments.add(allCurArgPossibilities);
			argConstraints.add(argConstraint);
		}
		List<Expression> newCalls = new ArrayList<Expression>();
		for (Expression e : getEquivalentExpressionsOrGiven(expression, new MethodConstraint(name, UnknownConstraint.getUnknownConstraint(), argConstraints)))
			if (e instanceof TypeLiteral || getDepth(e) < curDepth)
				makeAllCalls(method, name, e, newCalls, newArguments, new ArrayList<Expression>(newArguments.size()));
		for (Expression newCall : newCalls)
			addIfNew(curEquivalences, new EvaluatedExpression(newCall, type, value), valued);
	}
    
    /**
     * Class that checks whether an expression is unique.
     * An expression is unique iff it has no repeated variables
     * or integer constants.
     * TODO: Note that this disallows good expressions such as (x*2)-(y/2),
     * but also bad ones like (x+1)+1.
     */
    private static class UniqueASTChecker extends ASTVisitor {
    	
    	private final Set<String> seen;
    	private boolean isUnique;
    	
    	public UniqueASTChecker() {
    		seen = new HashSet<String>();
    		isUnique = true;
    	}
    	
    	@Override
    	public boolean visit(SimpleName node) {
    		visit(node.getIdentifier());
    		return true;
    	}
    	
    	@Override
    	public boolean visit(NumberLiteral node) {
    		visit(node.getToken());
    		return true;
    	}
    	
    	private void visit(String s) {
    		if (seen.contains(s))
    			isUnique = false;
    		else
    			seen.add(s);
    	}
    	
    	public boolean isUnique() {
    		return isUnique;
    	}
    	
    }
    
    /**
     * Checks whether a given expression is unique wrt UniqueASTChecker.
     * @param e The expression to check.
     * @return Whether or not the expression is unique wrt UniqueASTChecker.
     */
    private static boolean isUnique(TypedExpression e) {
    	UniqueASTChecker checker = new UniqueASTChecker();
    	e.getExpression().accept(checker);
    	return checker.isUnique();
    }
    
    /**
     * Gets the depth of the given expression.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    private static int getDepth(TypedExpression expr) {
    	Object depthProp = expr.getExpression().getProperty("depth");
    	if (depthProp != null)
    		return ((Integer)depthProp).intValue();
    	else
    		return getDepth(expr.getExpression());
    }

    /**
     * Gets the depth of the given expression.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    private static int getDepth(Expression expr) {
    	if (expr == null)
    		return 0;
    	if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral)
			return 0;
    	if (expr instanceof ParenthesizedExpression)
			return getDepth(((ParenthesizedExpression)expr).getExpression());
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			return Math.max(getDepth(infix.getLeftOperand()), getDepth(infix.getRightOperand())) + 1;
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			return Math.max(getDepth(array.getArray()), getDepth(array.getIndex())) + 1;
		} else if (expr instanceof FieldAccess) {
			return getDepth(((FieldAccess)expr).getExpression()) + 1;
		} else if (expr instanceof PrefixExpression) {
			return getDepth(((PrefixExpression)expr).getOperand()) + 1;
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepth(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				int curArgDepth = getDepth(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			return maxChildDepth + 1;
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepth(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				int curArgDepth = getDepth(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			return maxChildDepth + 1;
		} else if (expr instanceof CastExpression) {
			return getDepth(((CastExpression)expr).getExpression());
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
    }

}
