package codehint.exprgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.Method;

import codehint.EclipseUtils;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.exprgen.typeconstraint.DesiredType;
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.FieldNameConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.MethodNameConstraint;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.SupertypeSet;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.property.LambdaProperty;
import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;

public class ExpressionSkeleton {
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
    
    public final static String HOLE_SYNTAX = "??";
    
    private final String sugaredString;
    private final Expression expression;
    private final Map<String, HoleInfo> holeInfos;
	
	private ExpressionSkeleton(String sugaredString, Expression node, Map<String, HoleInfo> holeInfos) {
		this.sugaredString = sugaredString;
		this.expression = node;
		this.holeInfos = holeInfos;
	}

	public static ExpressionSkeleton fromString(String skeletonStr) {
		return SkeletonParser.rewriteHoleSyntax(skeletonStr);
	}
	
	public static String isLegalSkeleton(String str, String varTypeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
		return SkeletonParser.isLegalSkeleton(str, varTypeName, stackFrame, evaluationEngine);
	}
	
	public String getSugaredString() {
		return sugaredString;
	}
	
	private static class SkeletonParser {

	    private final static String DESUGARED_HOLE_NAME = "_$hole";
	
		private static class ParserError extends RuntimeException {
			
			private static final long serialVersionUID = 1L;
	
			public ParserError(String msg) {
				super(msg);
			}
			
		}
		
		public static String isLegalSkeleton(String str, String varTypeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
			try {
				ExpressionSkeleton skeleton = rewriteHoleSyntax(str);
				Set<String> compileErrors = EclipseUtils.getSetOfCompileErrors(skeleton.expression.toString(), varTypeName, stackFrame, evaluationEngine);
				if (compileErrors != null)
					for (String error: compileErrors)
						if (!error.contains(DESUGARED_HOLE_NAME))
							return error;
				return null;
			} catch (ParserError e) {
				return e.getMessage();
			}
		}
		
		private static ExpressionSkeleton rewriteHoleSyntax(String sugaredString) {
			String str = sugaredString;
			Map<String, HoleInfo> holeInfos = new HashMap<String, HoleInfo>();
			for (int holeNum = 0; true; holeNum++) {
				int holeStart = str.indexOf(HOLE_SYNTAX);
				if (holeStart == -1)
					break;
				else
					str = rewriteSingleHole(str, holeStart, holeNum, holeInfos);
			}
			if (holeInfos.isEmpty())
				throw new ParserError("The skeleton must have at least one " + HOLE_SYNTAX + " hole.");
			ASTNode node = EclipseUtils.parseExpr(parser, str);
			if (node instanceof CompilationUnit)
				throw new ParserError("Enter a valid skeleton: " + ((CompilationUnit)node).getProblems()[0].getMessage());
			return new ExpressionSkeleton(sugaredString, (Expression)node, holeInfos);
		}
	
		private static String rewriteSingleHole(String str, int holeIndex, int holeNum, Map<String, HoleInfo> holeInfos) {
			if (holeIndex >= 1 && Character.isJavaIdentifierPart(str.charAt(holeIndex - 1)))
				throw new ParserError("You cannot put a " + HOLE_SYNTAX + " hole immediately after an identifier.");
			String args = null;
			boolean isNegative = false;
			int i = holeIndex + 2;
			if (i + 1 < str.length() && str.charAt(i) == '-' && str.charAt(i + 1) == '{') {
				isNegative = true;
				i++;
			}
			if (i < str.length() && str.charAt(i) == '{') {
				while (i < str.length() && str.charAt(i) != '}')
					i++;
				args = str.substring(holeIndex + (isNegative ? 4 : 3), i);
				if (i < str.length())
					i++;
				else
					throw new ParserError("Enter a close brace } to finish the set of possibilities.");
			}
			if (i < str.length() && Character.isJavaIdentifierPart(str.charAt(i)))
				throw new ParserError("You cannot put a " + HOLE_SYNTAX + " hole immediately before an identifier.");
			String holeName = DESUGARED_HOLE_NAME + holeNum;
			str = str.substring(0, holeIndex) + holeName + str.substring(i);
			holeInfos.put(holeName, makeHoleInfo(args, isNegative));
			return str;
		}
		
		private static HoleInfo makeHoleInfo(String argsStr, boolean isNegative) {
			ArrayList<String> args = null;
			if (argsStr != null) {
				ASTNode node = EclipseUtils.parseExpr(parser, DESUGARED_HOLE_NAME + "(" + argsStr + ")");
				if (node instanceof MethodInvocation) {
					List<?> untypedArgs = ((MethodInvocation)node).arguments();
					args = new ArrayList<String>(untypedArgs.size());
					for (int i = 0; i < untypedArgs.size(); i++)
						args.add(((Expression)untypedArgs.get(i)).toString());
				} else
					throw new ParserError(((CompilationUnit)node).getProblems()[0].getMessage());
			}
			return new HoleInfo(args, isNegative);
		}
	
	}
	
	private static class HoleInfo {
		
		private final ArrayList<String> args;
		private final boolean isNegative;
		
		public HoleInfo(ArrayList<String> args, boolean isNegative) {
			this.args = args;
			this.isNegative = isNegative;
		}
		
		public ArrayList<String> getArgs() {
			return args;
		}
		
		public boolean isNegative() {
			return isNegative;
		}
		
		@Override
		public String toString() {
			return HOLE_SYNTAX + (isNegative ? "-" : "") + (args == null ? "" : args.toString());
		}
		
	}
	
	public ArrayList<EvaluatedExpression> synthesize(IJavaDebugTarget target, IJavaStackFrame stack, Property property, IJavaType varStaticType, IProgressMonitor monitor) {
		try {
			// TODO: Improve progress monitor so it shows you which evaluation it is.
			TypeConstraint typeConstraint = getInitialTypeConstraint(varStaticType, property, stack, target);
			if (HOLE_SYNTAX.equals(sugaredString))  // Optimization: Optimize special case of "??" skeleton by simply calling old ExprGen code directly.
				return ExpressionGenerator.generateExpression(target, stack, property, typeConstraint, new SubtypeChecker(), monitor, 1);
			monitor.beginTask("Skeleton generation", holeInfos.size() + 2);
			SkeletonFiller filler = SkeletonFiller.fillSkeleton(expression, typeConstraint, holeInfos, stack, target, monitor);
			ArrayList<TypedExpression> exprs = fillHolesWithValues(expression, filler, varStaticType, stack, monitor);
			SubMonitor evalMonitor = SubMonitor.convert(monitor, "Expression evaluation", exprs.size());
			ArrayList<EvaluatedExpression> results = EvaluationManager.evaluateExpressions(exprs, stack, property, evalMonitor);
	    	monitor.done();
			return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static TypeConstraint getInitialTypeConstraint(IJavaType varStaticType, Property property, IJavaStackFrame stack, IJavaDebugTarget target) throws DebugException {
		if (property instanceof ValueProperty)
			return new DesiredType(((ValueProperty)property).getValue().getJavaType());
		else if (property instanceof TypeProperty)
			return new SupertypeBound(EclipseUtils.getType(((TypeProperty)property).getTypeName(), stack, target));
		else if (property instanceof LambdaProperty) {
			String typeName = ((LambdaProperty)property).getTypeName();
			if (typeName != null)
				return new SupertypeBound(EclipseUtils.getType(typeName, stack, target));
		}
		return new SupertypeBound(varStaticType);
	}
	
	private static ArrayList<TypedExpression> fillHolesWithValues(Expression expr, SkeletonFiller filler, IJavaType varStaticType, IJavaStackFrame stack, IProgressMonitor monitor) {
		ArrayList<String> prevExpressions = new ArrayList<String>(1);
		prevExpressions.add(expr.toString());
		// TODO: Decomp
		for (Map.Entry<String, List<EvaluatedExpression>> entry: filler.getHoleValues().entrySet()) {
			ArrayList<String> curExpressions = new ArrayList<String>(prevExpressions.size() * entry.getValue().size());
			for (String oldExpression: prevExpressions)
				for (EvaluatedExpression curExpression: entry.getValue())
					curExpressions.add(oldExpression.replace(entry.getKey(), curExpression.getExpression().toString()));
			prevExpressions = curExpressions;
		}
		for (Map.Entry<String, List<Field>> entry: filler.getHoleFields().entrySet()) {
			ArrayList<String> curExpressions = new ArrayList<String>(prevExpressions.size() * entry.getValue().size());
			for (String oldExpression: prevExpressions)
				for (Field curField: entry.getValue())
					curExpressions.add(oldExpression.replace(entry.getKey(), curField.name()));
			prevExpressions = curExpressions;
		}
		for (Map.Entry<String, List<Method>> entry: filler.getHoleMethods().entrySet()) {
			ArrayList<String> curExpressions = new ArrayList<String>(prevExpressions.size() * entry.getValue().size());
			for (String oldExpression: prevExpressions)
				for (Method curMethod: entry.getValue())
					curExpressions.add(oldExpression.replace(entry.getKey(), curMethod.name()));
			prevExpressions = curExpressions;
		}
		return parseExpressions(prevExpressions, varStaticType, stack, monitor);
	}
	
	// TODO: Instead of doing this compile check, as we will that we only generate the right ones.  The call to filterCrashingExpression can be a big slowdown.
	private static ArrayList<TypedExpression> parseExpressions(ArrayList<String> expressionStrings, IJavaType varStaticType, IJavaStackFrame stack, IProgressMonitor monitor) {
		ArrayList<String> nonCrashingExpressionStrings = filterCrashingExpression(expressionStrings, varStaticType, stack, monitor);
		ArrayList<TypedExpression> result = new ArrayList<TypedExpression>(nonCrashingExpressionStrings.size());
		for (String s: nonCrashingExpressionStrings)
			result.add(new TypedExpression((Expression)EclipseUtils.parseExpr(parser, s), varStaticType, null));
		return result;
	}
	
	private static ArrayList<String> filterCrashingExpression(ArrayList<String> expressionStrings, IJavaType varStaticType, IJavaStackFrame stack, IProgressMonitor monitor) {
		try {
			SubMonitor evalMonitor = SubMonitor.convert(monitor, "Expression typecheck", expressionStrings.size());
			ArrayList<String> result = new ArrayList<String>();
			IAstEvaluationEngine engine = EclipseUtils.getASTEvaluationEngine(stack);
			String varStaticTypeName = varStaticType == null ? null : EclipseUtils.sanitizeTypename(varStaticType.getName());
			for (String s: expressionStrings) {
				if (EclipseUtils.getCompileErrors(s, varStaticTypeName, stack, engine) == null)
					result.add(s);
				evalMonitor.worked(1);
			}
			return result;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static class HoleParentSetter extends ASTVisitor {

		private final Map<String, HoleInfo> holeInfos;
		private final Set<ASTNode> parentsOfHoles;
		
		private HoleParentSetter(Map<String, HoleInfo> holeInfos) {
			this.holeInfos = holeInfos;
			parentsOfHoles = new HashSet<ASTNode>();
		}
		
		public static Set<ASTNode> getParentsOfHoles(Map<String, HoleInfo> holeInfos, Expression skeleton) {
			HoleParentSetter holeParentSetter = new HoleParentSetter(holeInfos);
			skeleton.accept(holeParentSetter);
			return holeParentSetter.parentsOfHoles;
		}

    	@Override
    	public boolean visit(SimpleName node) {
    		if (holeInfos.containsKey(node.getIdentifier()))
    			for (ASTNode cur = node; cur != null && cur instanceof Expression; cur = cur.getParent())
    				parentsOfHoles.add(cur);
    		return true;
    	}
		
	}
	
	private static class SkeletonFiller {

		private final Map<String, List<EvaluatedExpression>> holeValues;
		private final Map<String, List<Field>> holeFields;
		private final Map<String, List<Method>> holeMethods;
		private final Map<String, HoleInfo> holeInfos;
		private final SubtypeChecker subtypeChecker;
		private final IJavaStackFrame stack;
		private final IJavaDebugTarget target;
		private final IProgressMonitor monitor;
		
		private SkeletonFiller(Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IJavaDebugTarget target, IProgressMonitor monitor) {
			this.holeValues = new HashMap<String, List<EvaluatedExpression>>();
			this.holeFields = new HashMap<String, List<Field>>();
			this.holeMethods = new HashMap<String, List<Method>>();
			this.holeInfos = holeInfos;
			this.subtypeChecker = new SubtypeChecker();
			this.stack = stack;
			this.target = target;
			this.monitor = monitor;
		}
		
		public static SkeletonFiller fillSkeleton(Expression skeleton, TypeConstraint initialTypeConstraint, Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IJavaDebugTarget target, IProgressMonitor monitor) {
			SkeletonFiller filler = new SkeletonFiller(holeInfos, stack, target, monitor);
			filler.fillSkeleton(skeleton, initialTypeConstraint, HoleParentSetter.getParentsOfHoles(holeInfos, skeleton));
			return filler;
		}
		
		// TODO: Instead of simply setting the node's constraint to the new thing, should we also keep what we have from the old one?  E.g., in get{Fields,Methods}AndSetConstraint, the old constraint might be "thing with a field" and the new might be "thing of type Foo".  We want to combine those two.
		private TypeConstraint fillSkeleton(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			if (node instanceof ArrayAccess) {
				return fillArrayAccess(node, curConstraint, parentsOfHoles);
			} else if (node instanceof BooleanLiteral) {
	    		return new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target));
			} else if (node instanceof CastExpression) {
				CastExpression cast = (CastExpression)node;
				if (parentsOfHoles.contains(cast.getExpression())) {
					// TODO: Allow any type or ensure comparable with the given type?
					fillSkeleton(cast.getExpression(), curConstraint, parentsOfHoles);
				}
				return new SupertypeBound(EclipseUtils.getType(cast.getType().toString(), stack, target));
			} else if (node instanceof CharacterLiteral) {
	    		return new SupertypeBound(EclipseUtils.getFullyQualifiedType("char", target));
			} else if (node instanceof ClassInstanceCreation) {
				return fillClassInstanceCreation(node, parentsOfHoles);
			} else if (node instanceof ConditionalExpression) {
				return fillConditionalExpression(node, curConstraint, parentsOfHoles);
			} else if (node instanceof FieldAccess) {
				FieldAccess fieldAccess = (FieldAccess)node;
				return fillNormalField(fieldAccess, fieldAccess.getExpression(), fieldAccess.getName(), parentsOfHoles, curConstraint);
			} else if (node instanceof InfixExpression) {
				return fillInfixExpression(node, curConstraint, parentsOfHoles);
			} else if (node instanceof InstanceofExpression) {
				InstanceofExpression instance = (InstanceofExpression)node;
				if (parentsOfHoles.contains(instance.getLeftOperand()))
					fillSkeleton(instance.getLeftOperand(), new SupertypeBound(EclipseUtils.getFullyQualifiedType("java.lang.Object", target)), parentsOfHoles);
	    		return new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target));
			} else if (node instanceof MethodInvocation) {
				MethodInvocation call = (MethodInvocation)node;
				ArrayList<TypeConstraint> argTypes = getArgTypes(node, call.arguments(), parentsOfHoles);
				TypeConstraint receiverTypeConstraint = null;
				if (call.getExpression() != null) {
					TypeConstraint expressionConstraint = new MethodConstraint(parentsOfHoles.contains(call.getName()) ? null : call.getName().getIdentifier(), curConstraint, argTypes);
					receiverTypeConstraint = fillSkeleton(call.getExpression(), expressionConstraint, parentsOfHoles);
				} else
					receiverTypeConstraint = getThisConstraint();
				return fillMethod(call, call.getName(), call.arguments(), parentsOfHoles, curConstraint, argTypes, receiverTypeConstraint);
			} else if (node instanceof NullLiteral) {
	    		return null;
			} else if (node instanceof NumberLiteral) {
	    		return fillNumberLiteral(node);
			} else if (node instanceof ParenthesizedExpression) {
				return fillSkeleton(((ParenthesizedExpression)node).getExpression(), curConstraint, parentsOfHoles);
			} else if (node instanceof PostfixExpression) {
				return fillSkeleton(((PostfixExpression)node).getOperand(), curConstraint, parentsOfHoles);
			} else if (node instanceof PrefixExpression) {
				return fillSkeleton(((PrefixExpression)node).getOperand(), curConstraint, parentsOfHoles);
			} else if (node instanceof QualifiedName) {
				IJavaType type = EclipseUtils.getFullyQualifiedTypeIfExists(node.toString(), target);
				if (type != null)
					return new SupertypeBound(type);
				else {
					QualifiedName name = (QualifiedName)node;
					return fillNormalField(name, name.getQualifier(), name.getName(), parentsOfHoles, curConstraint);
				}
			} else if (node instanceof SimpleName) {
				return fillSimpleName(node, curConstraint);
			} else if (node instanceof StringLiteral) {
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("java.lang.String", target));
			} else if (node instanceof SuperFieldAccess) {
				SuperFieldAccess superAccess = (SuperFieldAccess)node;
				return fillField(superAccess.getName(), parentsOfHoles, curConstraint, new SupertypeBound(getSuperType(superAccess.getQualifier())));
			} else if (node instanceof SuperMethodInvocation) {
				SuperMethodInvocation superAccess = (SuperMethodInvocation)node;
				ArrayList<TypeConstraint> argTypes = getArgTypes(node, superAccess.arguments(), parentsOfHoles);
				return fillMethod(superAccess, superAccess.getName(), superAccess.arguments(), parentsOfHoles, curConstraint, argTypes, new SupertypeBound(getSuperType(superAccess.getQualifier())));
			} else if (node instanceof ThisExpression) {
				return getThisConstraint(); 
			} else if (node instanceof TypeLiteral) {
				return new SupertypeBound(EclipseUtils.getType(((TypeLiteral)node).getType().toString(), stack, target));
			} else
				throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
		}

		private TypeConstraint fillArrayAccess(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			try {
				ArrayAccess arrayAccess = (ArrayAccess)node;
				TypeConstraint arrayConstraint = null;
				if (parentsOfHoles.contains(arrayAccess.getArray())) {
					IJavaType[] componentConstraints = curConstraint.getTypes(target);
					List<IJavaType> componentTypes = new ArrayList<IJavaType>(componentConstraints.length);
					for (IJavaType type: componentConstraints)
						componentTypes.add(EclipseUtils.getFullyQualifiedType(type.getName() + "[]", target));
					arrayConstraint = getSupertypeConstraintForTypes(componentTypes);
				}
				arrayConstraint = fillSkeleton(arrayAccess.getArray(), arrayConstraint, parentsOfHoles);
				if (parentsOfHoles.contains(arrayAccess.getIndex()))
					fillSkeleton(arrayAccess.getIndex(), new SupertypeBound(EclipseUtils.getFullyQualifiedType("int", target)), parentsOfHoles);
				IJavaType[] arrayConstraints = arrayConstraint.getTypes(target);
				List<IJavaType> resultTypes = new ArrayList<IJavaType>(arrayConstraints.length);
				for (IJavaType type: arrayConstraints)
					resultTypes.add(((IJavaArrayType)type).getComponentType());
				return getSupertypeConstraintForTypes(resultTypes);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		private TypeConstraint fillClassInstanceCreation(Expression node, Set<ASTNode> parentsOfHoles) {
			ClassInstanceCreation cons = (ClassInstanceCreation)node;
			assert cons.getExpression() == null;  // TODO: Handle this case.
			IJavaType consType = EclipseUtils.getType(cons.getType().toString(), stack, target);
			ArrayList<TypeConstraint> argTypes = getArgTypes(node, cons.arguments(), parentsOfHoles);
			List<Method> constructors = null;
			for (int i = 0; i < cons.arguments().size(); i++) {
				Expression arg = (Expression)cons.arguments().get(i);
				if (parentsOfHoles.contains(arg)) {
					if (constructors == null)
						constructors = getConstructors(consType, argTypes);
					fillArg(arg, i, constructors, parentsOfHoles);
				}
			}
			return new DesiredType(consType);
		}

		private TypeConstraint fillConditionalExpression(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ConditionalExpression cond = (ConditionalExpression)node;
			if (parentsOfHoles.contains(cond.getExpression()))
				fillSkeleton(cond.getExpression(), new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target)), parentsOfHoles);
			TypeConstraint thenConstraint = fillSkeleton(cond.getThenExpression(), curConstraint, parentsOfHoles);
			if (parentsOfHoles.contains(cond.getElseExpression()))
				fillSkeleton(cond.getElseExpression(), curConstraint, parentsOfHoles);
			return thenConstraint;
			// TODO: Constrain left/right to be similar.
		}

		private TypeConstraint fillInfixExpression(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			InfixExpression infix = (InfixExpression)node;
			boolean isBooleanResult = infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR || infix.getOperator() == InfixExpression.Operator.EQUALS || infix.getOperator() == InfixExpression.Operator.NOT_EQUALS || infix.getOperator() == InfixExpression.Operator.LESS || infix.getOperator() == InfixExpression.Operator.LESS_EQUALS || infix.getOperator() == InfixExpression.Operator.GREATER || infix.getOperator() == InfixExpression.Operator.GREATER_EQUALS;
			boolean leftHasHole = parentsOfHoles.contains(infix.getLeftOperand());
			boolean rightHasHole = parentsOfHoles.contains(infix.getRightOperand());
			if (isBooleanResult && !leftHasHole && !rightHasHole)
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target));
			TypeConstraint leftConstraint = curConstraint;
			TypeConstraint rightConstraint = curConstraint;
			if (infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) {
				leftConstraint = new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target));
				if (parentsOfHoles.contains(infix.getRightOperand()))
					rightConstraint = new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target));
			}
			leftConstraint = fillSkeleton(infix.getLeftOperand(), leftConstraint, parentsOfHoles);
			if (rightHasHole)
				fillSkeleton(infix.getRightOperand(), rightConstraint, parentsOfHoles);
			if (isBooleanResult)
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("boolean", target));
			else
				return leftConstraint;
			// TODO: Constrain left/right to be similar.
			// TODO: For Strings and +, ensure that lhs and rhs are not both null.
		}

		private TypeConstraint fillNumberLiteral(Expression node) {
			String str = ((NumberLiteral)node).getToken();
			int lastChar = str.charAt(str.length() - 1);
			// Rules taken from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html.
			if (lastChar == 'l' || lastChar == 'L')
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("long", target));
			else if (lastChar == 'f' || lastChar == 'f')
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("float", target));
			else if (lastChar == 'd' || lastChar == 'd' || str.indexOf('.') != -1)
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("double", target));
			else
				return new SupertypeBound(EclipseUtils.getFullyQualifiedType("int", target));
		}

		private TypeConstraint fillSimpleName(Expression node, TypeConstraint curConstraint) {
			try {
				SimpleName name = (SimpleName)node;
				if (holeInfos.containsKey(name.getIdentifier())) {
					HoleInfo holeInfo = holeInfos.get(name.getIdentifier());
					SubMonitor childMonitor = SubMonitor.convert(monitor, "Hole generation and evaluation", 1);
					assert !holeInfo.isNegative();  // TODO: Use negative arg information.
					// TODO: Optimization: If this is the only hole, evaluate the skeleton directly?
					// TODO: Improve heuristics to reduce search space when too many holes.
					if (curConstraint instanceof FieldNameConstraint) {
						FieldNameConstraint fieldNameConstraint = (FieldNameConstraint)curConstraint;
						if (holeInfo.getArgs() != null)
							fieldNameConstraint.setLegalNames(new HashSet<String>(holeInfo.getArgs()));
						return getFieldsAndSetConstraint(name, fieldNameConstraint, true);
					} else if (curConstraint instanceof MethodNameConstraint) {
						MethodNameConstraint methodNameConstraint = (MethodNameConstraint)curConstraint;
						if (holeInfo.getArgs() != null)
							methodNameConstraint.setLegalNames(new HashSet<String>(holeInfo.getArgs()));
						return getMethodsAndSetConstraint(name, methodNameConstraint, true);
					} else {
						List<EvaluatedExpression> values;
						if (holeInfo.getArgs() != null) {
							ArrayList<String> nonCrashingStrings = filterCrashingExpression(holeInfo.getArgs(), null, stack, childMonitor);
							ArrayList<TypedExpression> fakeTypedHoleInfos = new ArrayList<TypedExpression>(nonCrashingStrings.size());
							for (String s: nonCrashingStrings)
								fakeTypedHoleInfos.add(new TypedExpression((Expression)EclipseUtils.parseExpr(parser, s), null, null));
							values = EvaluationManager.evaluateExpressions(fakeTypedHoleInfos, stack, null, childMonitor);
						} else
							values = ExpressionGenerator.generateExpression(target, stack, null, curConstraint, subtypeChecker, childMonitor, holeInfos.size() == 1 ? 1 : 0);
						holeValues.put(name.getIdentifier(), values);
						List<IJavaType> types = new ArrayList<IJavaType>(values.size());
						for (EvaluatedExpression e: values)
							types.add(e.getType());
						return getSupertypeConstraintForTypes(types);
					}
				} else if (curConstraint instanceof FieldNameConstraint) {
					return getFieldsAndSetConstraint(name, (FieldNameConstraint)curConstraint, false);
				} else if (curConstraint instanceof MethodNameConstraint) {
					return getMethodsAndSetConstraint(name, (MethodNameConstraint)curConstraint, false);
				} else {
					IJavaVariable var = stack.findVariable(name.getIdentifier());
					if (var != null)
						return new SupertypeBound(var.getJavaType());
					else
						return null;
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			} catch (ClassNotLoadedException e) {
				throw new RuntimeException(e);
			}
		}
		
		/* Helper functions. */

		private TypeConstraint getFieldsAndSetConstraint(SimpleName name, FieldNameConstraint fieldNameConstraint, boolean isHole) throws ClassNotLoadedException {
			ArrayList<Field> fields = fieldNameConstraint.getFields(target, subtypeChecker);
			List<IJavaType> types = new ArrayList<IJavaType>(fields.size());
			for (Field field: fields)
				types.add(EclipseUtils.getFullyQualifiedType(field.type().name(), target));
			if (isHole)
				holeFields.put(name.getIdentifier(), fields);
			return getSupertypeConstraintForTypes(types);
		}

		private TypeConstraint getMethodsAndSetConstraint(SimpleName name, MethodNameConstraint methodNameConstraint, boolean isHole) throws ClassNotLoadedException {
			ArrayList<Method> methods = methodNameConstraint.getMethods(stack, target, subtypeChecker);
			List<IJavaType> types = new ArrayList<IJavaType>(methods.size());
			for (Method method: methods)
				types.add(EclipseUtils.getFullyQualifiedTypeIfExists(method.returnType().name(), target));
			if (isHole)
				holeMethods.put(name.getIdentifier(), methods);
			return getSupertypeConstraintForTypes(types);
		}

		private TypeConstraint fillNormalField(Expression node, Expression qualifier, SimpleName name, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint) {
			TypeConstraint qualifierConstraint = new FieldConstraint(parentsOfHoles.contains(name) ? null : name.getIdentifier(), curConstraint);
			TypeConstraint receiverTypeConstraint = fillSkeleton(qualifier, qualifierConstraint, parentsOfHoles);
			return fillField(name, parentsOfHoles, curConstraint, receiverTypeConstraint);
		}

		private TypeConstraint fillField(SimpleName name, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint, TypeConstraint receiverTypeConstraint) {
			return fillSkeleton(name, new FieldNameConstraint(receiverTypeConstraint, curConstraint), parentsOfHoles);
		}

		private TypeConstraint fillMethod(Expression node, SimpleName name, List<?> arguments, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint, ArrayList<TypeConstraint> argTypes, TypeConstraint receiverTypeConstraint) {
			MethodNameConstraint methodNameConstraint = new MethodNameConstraint(receiverTypeConstraint, curConstraint, argTypes);
			TypeConstraint methodTypeConstraint = fillSkeleton(name, methodNameConstraint, parentsOfHoles);
			List<Method> methods = null;
			for (int i = 0; i < arguments.size(); i++) {
				Expression arg = (Expression)arguments.get(i);
				if (parentsOfHoles.contains(arg)) {
					if (methods == null) {
						if (holeMethods.containsKey(name.getIdentifier()))
							methods = holeMethods.get(name.getIdentifier());
						else
							methods = methodNameConstraint.getMethods(stack, target, subtypeChecker);
					}
					fillArg(arg, i, methods, parentsOfHoles);
				}
			}
			return methodTypeConstraint;
		}
		
		private ArrayList<TypeConstraint> getArgTypes(Expression node, List<?> argNodes, Set<ASTNode> parentsOfHoles) {
			if (parentsOfHoles.contains(node)) {
				ArrayList<TypeConstraint> argTypes;
				argTypes = new ArrayList<TypeConstraint>(argNodes.size());
				for (Object argObj: argNodes) {
					Expression arg = (Expression)argObj;
					argTypes.add(parentsOfHoles.contains(arg) ? null : fillSkeleton(arg, null, parentsOfHoles));
				}
				return argTypes;
			} else
				return null;
		}

		private void fillArg(Expression arg, int i, List<Method> methods, Set<ASTNode> parentsOfHoles) {
			List<IJavaType> argConstraints = new ArrayList<IJavaType>(methods.size());
			for (Method method: methods)
				argConstraints.add(EclipseUtils.getFullyQualifiedType((String)method.argumentTypeNames().get(i), target));
			TypeConstraint argConstraint = getSupertypeConstraintForTypes(argConstraints);
			fillSkeleton(arg, argConstraint, parentsOfHoles);
		}
		
		private static TypeConstraint getSupertypeConstraintForTypes(Collection<IJavaType> types) {
			try {
				// Putting IJavaTypes into a Set doesn't remove duplicates, so I need to map them based on their names.
				Map<String, IJavaType> typesMap = new HashMap<String, IJavaType>();
				for (IJavaType type: types) {
					if (type != null) {  // Ignore nulls.
						String typeName = type.getName();
						if (!typesMap.containsKey(typeName))
							typesMap.put(typeName, type);
					}
				}
				Collection<IJavaType> uniqueTypes = typesMap.values();
				return uniqueTypes.size() == 1 ? new SupertypeBound(uniqueTypes.iterator().next()) : new SupertypeSet(new HashSet<IJavaType>(uniqueTypes));
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		private TypeConstraint getThisConstraint() {
			try {
				return new SupertypeBound(stack.getReferenceType());
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		private IJavaType getSuperType(Name name) {
    		try {
    			IJavaType curType = name == null ? stack.getReferenceType() : EclipseUtils.getType(name.toString(), stack, target);
				return ((IJavaClassType)curType).getSuperclass();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
    	
    	private ArrayList<Method> getConstructors(IJavaType type, ArrayList<TypeConstraint> argConstraints) {
			try {
	    		ArrayList<Method> methods = new ArrayList<Method>();
	    		for (Method method: ExpressionGenerator.getMethods(type))
					if (ExpressionGenerator.isLegalMethod(method, stack.getReferenceType(), true) && MethodNameConstraint.fulfillsArgConstraints(method, argConstraints, subtypeChecker, target))
						methods.add(method);
	    		return methods;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
    	}
    	
    	// Getters
    	
    	public Map<String, List<EvaluatedExpression>> getHoleValues() {
    		return holeValues;
    	}
    	
    	public Map<String, List<Field>> getHoleFields() {
    		return holeFields;
    	}
    	
    	public Map<String, List<Method>> getHoleMethods() {
    		return holeMethods;
    	}
		
	}

}
