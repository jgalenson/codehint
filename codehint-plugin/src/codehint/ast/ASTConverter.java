package codehint.ast;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTParser;

import codehint.ast.ASTVisitor;
import codehint.ast.ArrayAccess;
import codehint.ast.ArrayType;
import codehint.ast.Assignment;
import codehint.ast.BooleanLiteral;
import codehint.ast.CastExpression;
import codehint.ast.CharacterLiteral;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.ConditionalExpression;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.InstanceofExpression;
import codehint.ast.MethodInvocation;
import codehint.ast.Name;
import codehint.ast.NullLiteral;
import codehint.ast.NumberLiteral;
import codehint.ast.ParenthesizedExpression;
import codehint.ast.PostfixExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.PrimitiveType;
import codehint.ast.QualifiedName;
import codehint.ast.QualifiedType;
import codehint.ast.SimpleName;
import codehint.ast.SimpleType;
import codehint.ast.StringLiteral;
import codehint.ast.SuperFieldAccess;
import codehint.ast.SuperMethodInvocation;
import codehint.ast.ThisExpression;
import codehint.ast.Type;
import codehint.ast.TypeLiteral;
import codehint.utils.EclipseUtils;

/**
 * Converts from the Eclipse AST into ours.
 */
public final class ASTConverter extends ASTVisitor {
	
	public static ASTNode parseExpr(ASTParser parser, String str) {
		return copy(EclipseUtils.parseExpr(parser, str));
	}
	
	private static ASTNode copy(org.eclipse.jdt.core.dom.ASTNode node) {
		if (node == null)
			return null;
		else if (node instanceof org.eclipse.jdt.core.dom.Expression)
			return copy((org.eclipse.jdt.core.dom.Expression)node);
		else if (node instanceof org.eclipse.jdt.core.dom.CompilationUnit)
			return new CompilationUnit(((org.eclipse.jdt.core.dom.CompilationUnit)node).getProblems());
		else
			throw new IllegalArgumentException("Unexpected ast node " + node.getClass().toString());
	}
	
	/**
	 * Makes a copy of the given Expression.
	 * Note that we do not use ASTNode.copySubtree
	 * because it does not copy properties.
	 * @param node The node to copy.
	 * @return A copy of the given node.
	 */
	public static Expression copy(org.eclipse.jdt.core.dom.Expression node) {
		if (node == null)
			return null;
		else if (node instanceof org.eclipse.jdt.core.dom.ArrayAccess) {
			return copy((org.eclipse.jdt.core.dom.ArrayAccess)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.BooleanLiteral) {
			return copy((org.eclipse.jdt.core.dom.BooleanLiteral)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.CastExpression) {
			return copy((org.eclipse.jdt.core.dom.CastExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.CharacterLiteral) {
			return copy((org.eclipse.jdt.core.dom.CharacterLiteral)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation) {
			return copy((org.eclipse.jdt.core.dom.ClassInstanceCreation)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.ConditionalExpression) {
			return copy((org.eclipse.jdt.core.dom.ConditionalExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.FieldAccess) {
			return copy((org.eclipse.jdt.core.dom.FieldAccess)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.InfixExpression) {
			return copy((org.eclipse.jdt.core.dom.InfixExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.InstanceofExpression) {
			return copy((org.eclipse.jdt.core.dom.InstanceofExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.MethodInvocation) {
			return copy((org.eclipse.jdt.core.dom.MethodInvocation)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.Name) {
			return copy((org.eclipse.jdt.core.dom.Name)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.NullLiteral) {
			return copy((org.eclipse.jdt.core.dom.NullLiteral)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.NumberLiteral) {
			return copy((org.eclipse.jdt.core.dom.NumberLiteral)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression) {
			return copy((org.eclipse.jdt.core.dom.ParenthesizedExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.PostfixExpression) {
			return copy((org.eclipse.jdt.core.dom.PostfixExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.PrefixExpression) {
			return copy((org.eclipse.jdt.core.dom.PrefixExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.StringLiteral) {
			return copy((org.eclipse.jdt.core.dom.StringLiteral)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.SuperFieldAccess) {
			return copy((org.eclipse.jdt.core.dom.SuperFieldAccess)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.SuperMethodInvocation) {
			return copy((org.eclipse.jdt.core.dom.SuperMethodInvocation)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.ThisExpression) {
			return copy((org.eclipse.jdt.core.dom.ThisExpression)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.TypeLiteral) {
			return copy((org.eclipse.jdt.core.dom.TypeLiteral)node);
		} else if (node instanceof org.eclipse.jdt.core.dom.Assignment) {
			return copy((org.eclipse.jdt.core.dom.Assignment)node);
		} else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	public static Name copy(org.eclipse.jdt.core.dom.Name name) {
		if (name == null)
			return null;
		if (name instanceof org.eclipse.jdt.core.dom.SimpleName)
			return copy((org.eclipse.jdt.core.dom.SimpleName)name);
		else if (name instanceof org.eclipse.jdt.core.dom.QualifiedName)
			return copy((org.eclipse.jdt.core.dom.QualifiedName)name);
		else
			throw new RuntimeException("Unexpected expression type " + name.getClass().toString());
	}

	/**
	 * Makes a copy of the given Type.
	 * Note that we do not use ASTNode.copySubtree
	 * because it does not copy properties.
	 * @param node The node to copy.
	 * @return A copy of the given node.
	 */
	public static Type copy(org.eclipse.jdt.core.dom.Type node) {
		if (node == null)
			return null;
		else if (node instanceof org.eclipse.jdt.core.dom.SimpleType)
			return new SimpleType(null, copy(((org.eclipse.jdt.core.dom.SimpleType)node).getName()));
		else if (node instanceof org.eclipse.jdt.core.dom.QualifiedType) {
			org.eclipse.jdt.core.dom.QualifiedType type = (org.eclipse.jdt.core.dom.QualifiedType)node;
			return new QualifiedType(null, copy(type.getQualifier()), copy(type.getName()));
		} else if (node instanceof org.eclipse.jdt.core.dom.ArrayType) {
			return new ArrayType(null, copy(((org.eclipse.jdt.core.dom.ArrayType)node).getComponentType()));
		} else if (node instanceof org.eclipse.jdt.core.dom.PrimitiveType) {
			return new PrimitiveType(null, copy(((org.eclipse.jdt.core.dom.PrimitiveType)node).getPrimitiveTypeCode()));
		} else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	private static PrimitiveType.Code copy(org.eclipse.jdt.core.dom.PrimitiveType.Code node) {
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.INT)
			return PrimitiveType.INT;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.CHAR)
			return PrimitiveType.CHAR;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.BOOLEAN)
			return PrimitiveType.BOOLEAN;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.SHORT)
			return PrimitiveType.SHORT;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.LONG)
			return PrimitiveType.LONG;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.FLOAT)
			return PrimitiveType.FLOAT;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.DOUBLE)
			return PrimitiveType.DOUBLE;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.BYTE)
			return PrimitiveType.BYTE;
		if (node == org.eclipse.jdt.core.dom.PrimitiveType.VOID)
			return PrimitiveType.VOID;
		throw new IllegalArgumentException(node.toString());
	}
	
	private static ArrayAccess copy(org.eclipse.jdt.core.dom.ArrayAccess node) {
		return new ArrayAccess(null, copy(node.getArray()), copy(node.getIndex()));
	}

	private static BooleanLiteral copy(org.eclipse.jdt.core.dom.BooleanLiteral node) {
		return new BooleanLiteral(null, node.booleanValue());
	}

	private static CastExpression copy(org.eclipse.jdt.core.dom.CastExpression node) {
		return new CastExpression(copy(node.getType()), copy(node.getExpression()));
	}

	private static CharacterLiteral copy(org.eclipse.jdt.core.dom.CharacterLiteral node) {
		return new CharacterLiteral(null, node.charValue());
	}

	private static ClassInstanceCreation copy(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
		return new ClassInstanceCreation(copy(node.getType()), copyList(node.arguments()));
	}

	private static ConditionalExpression copy(org.eclipse.jdt.core.dom.ConditionalExpression node) {
		return new ConditionalExpression(null, copy(node.getExpression()), copy(node.getThenExpression()), copy(node.getElseExpression()));
	}

	private static FieldAccess copy(org.eclipse.jdt.core.dom.FieldAccess node) {
		return new FieldAccess(null, copy(node.getExpression()), copy(node.getName()));
	}

	private static InfixExpression copy(org.eclipse.jdt.core.dom.InfixExpression node) {
		InfixExpression result = new InfixExpression(null, copy(node.getLeftOperand()), copy(node.getOperator()), copy(node.getRightOperand()));
		for (Object extraOperand: node.extendedOperands())
			result = new InfixExpression(null, result, result.getOperator(), copy((org.eclipse.jdt.core.dom.Expression)extraOperand));
		return result;
	}
	
	public static InfixExpression.Operator copy(org.eclipse.jdt.core.dom.InfixExpression.Operator node) {
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.TIMES)
			return InfixExpression.Operator.TIMES;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.DIVIDE)
			return InfixExpression.Operator.DIVIDE;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.REMAINDER)
			return InfixExpression.Operator.REMAINDER;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.PLUS)
			return InfixExpression.Operator.PLUS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.MINUS)
			return InfixExpression.Operator.MINUS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.LEFT_SHIFT)
			return InfixExpression.Operator.LEFT_SHIFT;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.RIGHT_SHIFT_SIGNED)
			return InfixExpression.Operator.RIGHT_SHIFT_SIGNED;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED)
			return InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.LESS)
			return InfixExpression.Operator.LESS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.GREATER)
			return InfixExpression.Operator.GREATER;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.LESS_EQUALS)
			return InfixExpression.Operator.LESS_EQUALS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.GREATER_EQUALS)
			return InfixExpression.Operator.GREATER_EQUALS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.EQUALS)
			return InfixExpression.Operator.EQUALS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.NOT_EQUALS)
			return InfixExpression.Operator.NOT_EQUALS;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.XOR)
			return InfixExpression.Operator.XOR;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.OR)
			return InfixExpression.Operator.OR;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.AND)
			return InfixExpression.Operator.AND;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.CONDITIONAL_OR)
			return InfixExpression.Operator.CONDITIONAL_OR;
		if (node == org.eclipse.jdt.core.dom.InfixExpression.Operator.CONDITIONAL_AND)
			return InfixExpression.Operator.CONDITIONAL_AND;
		throw new IllegalArgumentException(node.toString());
	}

	private static InstanceofExpression copy(org.eclipse.jdt.core.dom.InstanceofExpression node) {
		return new InstanceofExpression(null, copy(node.getLeftOperand()), copy(node.getRightOperand()));
	}

	private static MethodInvocation copy(org.eclipse.jdt.core.dom.MethodInvocation node) {
		return new MethodInvocation(null, copy(node.getExpression()), copy(node.getName()), copyList(node.arguments()));
	}

	private static NullLiteral copy(@SuppressWarnings("unused") org.eclipse.jdt.core.dom.NullLiteral node) {
		return NullLiteral.getNullLiteral();
	}
	
	private static NumberLiteral copy(org.eclipse.jdt.core.dom.NumberLiteral node) {
		String str = node.getToken();
		int lastChar = str.charAt(str.length() - 1);
		// Rules taken from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html.
		if (lastChar == 'l' || lastChar == 'L')
			return new LongLiteral(null, Long.parseLong(str));
		else if (lastChar == 'f' || lastChar == 'F')
			return new FloatLiteral(null, Float.parseFloat(str));
		else if (lastChar == 'd' || lastChar == 'D')
			return new DoubleLiteral(null, Double.parseDouble(str));
		else
			return new IntLiteral(null, Integer.parseInt(str));
	}

	private static ParenthesizedExpression copy(org.eclipse.jdt.core.dom.ParenthesizedExpression node) {
		return new ParenthesizedExpression(copy(node.getExpression()));
	}

	private static PostfixExpression copy(org.eclipse.jdt.core.dom.PostfixExpression node) {
		return new PostfixExpression(copy(node.getOperand()), copy(node.getOperator()));
	}
	
	public static PostfixExpression.Operator copy(org.eclipse.jdt.core.dom.PostfixExpression.Operator node) {
		if (node == org.eclipse.jdt.core.dom.PostfixExpression.Operator.INCREMENT)
			return PostfixExpression.Operator.INCREMENT;
		if (node == org.eclipse.jdt.core.dom.PostfixExpression.Operator.DECREMENT)
			return PostfixExpression.Operator.DECREMENT;
		throw new IllegalArgumentException(node.toString());
	}

	private static PrefixExpression copy(org.eclipse.jdt.core.dom.PrefixExpression node) {
		return new PrefixExpression(copy(node.getOperator()), copy(node.getOperand()));
	}
	
	public static PrefixExpression.Operator copy(org.eclipse.jdt.core.dom.PrefixExpression.Operator node) {
		if (node == org.eclipse.jdt.core.dom.PrefixExpression.Operator.INCREMENT)
			return PrefixExpression.Operator.INCREMENT;
		if (node == org.eclipse.jdt.core.dom.PrefixExpression.Operator.DECREMENT)
			return PrefixExpression.Operator.DECREMENT;
		if (node == org.eclipse.jdt.core.dom.PrefixExpression.Operator.PLUS)
			return PrefixExpression.Operator.PLUS;
		if (node == org.eclipse.jdt.core.dom.PrefixExpression.Operator.MINUS)
			return PrefixExpression.Operator.MINUS;
		if (node == org.eclipse.jdt.core.dom.PrefixExpression.Operator.COMPLEMENT)
			return PrefixExpression.Operator.COMPLEMENT;
		if (node == org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT)
			return PrefixExpression.Operator.NOT;
		throw new IllegalArgumentException(node.toString());
	}

	private static QualifiedName copy(org.eclipse.jdt.core.dom.QualifiedName node) {
		return new QualifiedName(null, copy(node.getQualifier()), copy(node.getName()));
	}

	private static SimpleName copy(org.eclipse.jdt.core.dom.SimpleName node) {
		return new SimpleName(null, node.getIdentifier());
	}

	private static StringLiteral copy(org.eclipse.jdt.core.dom.StringLiteral node) {
		return new StringLiteral(null, node.getLiteralValue());
	}

	private static SuperFieldAccess copy(org.eclipse.jdt.core.dom.SuperFieldAccess node) {
		return new SuperFieldAccess(null, copy(node.getQualifier()), copy(node.getName()));
	}

	private static SuperMethodInvocation copy(org.eclipse.jdt.core.dom.SuperMethodInvocation node) {
		return new SuperMethodInvocation(null, copy(node.getQualifier()), copy(node.getName()), copyList(node.arguments()));
	}

	private static ThisExpression copy(org.eclipse.jdt.core.dom.ThisExpression node) {
		return new ThisExpression(null, copy(node.getQualifier()));
	}

	private static TypeLiteral copy(org.eclipse.jdt.core.dom.TypeLiteral node) {
		return new TypeLiteral(copy(node.getType()));
	}

	private static Assignment copy(org.eclipse.jdt.core.dom.Assignment node) {
		return new Assignment(null, copy(node.getLeftHandSide()), copy(node.getOperator()), copy(node.getRightHandSide()));
	}
	
	private static Assignment.Operator copy(org.eclipse.jdt.core.dom.Assignment.Operator node) {
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN)
			return Assignment.Operator.ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.PLUS_ASSIGN)
			return Assignment.Operator.PLUS_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.MINUS_ASSIGN)
			return Assignment.Operator.MINUS_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.TIMES_ASSIGN)
			return Assignment.Operator.TIMES_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.DIVIDE_ASSIGN)
			return Assignment.Operator.DIVIDE_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.BIT_AND_ASSIGN)
			return Assignment.Operator.BIT_AND_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.BIT_OR_ASSIGN)
			return Assignment.Operator.BIT_OR_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.BIT_XOR_ASSIGN)
			return Assignment.Operator.BIT_XOR_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.REMAINDER_ASSIGN)
			return Assignment.Operator.REMAINDER_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.LEFT_SHIFT_ASSIGN)
			return Assignment.Operator.LEFT_SHIFT_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN)
			return Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN;
		if (node == org.eclipse.jdt.core.dom.Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN)
			return Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN;
		throw new IllegalArgumentException(node.toString());
	}

	private static Expression[] copyList(List<?> orig) {
		Expression[] copy = new Expression[orig.size()];
		for (int i = 0; i < copy.length; i++)
			copy[i] = copy((org.eclipse.jdt.core.dom.Expression)orig.get(i));
		return copy;
	}

}
