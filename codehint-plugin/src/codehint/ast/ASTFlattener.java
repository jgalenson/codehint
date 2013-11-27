package codehint.ast;

/**
 * Adapted from org.eclipse.jdt.internal.core.dom.NaiveASTFlattener.
 */
public abstract class ASTFlattener extends ASTVisitor {
	
	public String getResult(ASTNode node) {
		StringBuilder sb = new StringBuilder();
		flatten(node, sb);
		return sb.toString();
	}
	
	protected void flatten(ASTNode node, StringBuilder sb) {
		if (node instanceof Expression)
			flatten((Expression)node, sb);
		else if (node instanceof Type)
			flatten((Type)node, sb);
		 else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	protected void flatten(Expression node, StringBuilder sb) {
		if (node instanceof ArrayAccess) {
			flatten((ArrayAccess)node, sb);
		} else if (node instanceof BooleanLiteral) {
			flatten((BooleanLiteral)node, sb);
		} else if (node instanceof CastExpression) {
			flatten((CastExpression)node, sb);
		} else if (node instanceof CharacterLiteral) {
			flatten((CharacterLiteral)node, sb);
		} else if (node instanceof ClassInstanceCreation) {
			flatten((ClassInstanceCreation)node, sb);
		} else if (node instanceof ConditionalExpression) {
			flatten((ConditionalExpression)node, sb);
		} else if (node instanceof FieldAccess) {
			flatten((FieldAccess)node, sb);
		} else if (node instanceof InfixExpression) {
			flatten((InfixExpression)node, sb);
		} else if (node instanceof InstanceofExpression) {
			flatten((InstanceofExpression)node, sb);
		} else if (node instanceof IntLiteral) {
			flatten((IntLiteral)node, sb);
		} else if (node instanceof MethodInvocation) {
			flatten((MethodInvocation)node, sb);
		} else if (node instanceof NullLiteral) {
			flatten((NullLiteral)node, sb);
		} else if (node instanceof ParenthesizedExpression) {
			flatten((ParenthesizedExpression)node, sb);
		} else if (node instanceof PostfixExpression) {
			flatten((PostfixExpression)node, sb);
		} else if (node instanceof PrefixExpression) {
			flatten((PrefixExpression)node, sb);
		} else if (node instanceof QualifiedName) {
			flatten((QualifiedName)node, sb);
		} else if (node instanceof SimpleName) {
			flatten((SimpleName)node, sb);
		} else if (node instanceof StringLiteral) {
			flatten((StringLiteral)node, sb);
		} else if (node instanceof SuperFieldAccess) {
			flatten((SuperFieldAccess)node, sb);
		} else if (node instanceof SuperMethodInvocation) {
			flatten((SuperMethodInvocation)node, sb);
		} else if (node instanceof ThisExpression) {
			flatten((ThisExpression)node, sb);
		} else if (node instanceof TypeLiteral) {
			flatten((TypeLiteral)node, sb);
		} else if (node instanceof Assignment) {
			flatten((Assignment)node, sb);
		} else if (node instanceof ArrayCreation) {
			flatten((ArrayCreation)node, sb);
		} else if (node instanceof ArrayInitializer) {
			flatten((ArrayInitializer)node, sb);
		} else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	protected void flatten(Type node, StringBuilder sb) {
		sb.append(node.toString());
	}
	
	protected void flatten(ArrayAccess node, StringBuilder sb) {
		flatten(node.getArray(), sb);
		sb.append("[");
		flatten(node.getIndex(), sb);
		sb.append("]");
	}

	protected void flatten(BooleanLiteral node, StringBuilder sb) {
		if (node.booleanValue() == true)
			sb.append("true");
		else
			sb.append("false");
	}

	protected void flatten(CastExpression node, StringBuilder sb) {
		sb.append("(");
		flatten(node.getType(), sb);
		sb.append(")");
		flatten(node.getExpression(), sb);
	}

	protected void flatten(CharacterLiteral node, StringBuilder sb) {
		sb.append(node.getEscapedValue());
	}

	protected void flatten(ClassInstanceCreation node, StringBuilder sb) {
		sb.append("new ");
		if (node.typeArguments().length > 0) {
			sb.append("<");
			doList(node.typeArguments(), sb);
			sb.append(">");
		}
		flatten(node.getType(), sb);
		sb.append("(");
		doList(node.arguments(), sb);
		sb.append(")");
	}

	protected void flatten(ConditionalExpression node, StringBuilder sb) {
		flatten(node.getExpression(), sb);
		sb.append(" ? ");
		flatten(node.getThenExpression(), sb);
		sb.append(" : ");
		flatten(node.getElseExpression(), sb);
	}

	protected void flatten(FieldAccess node, StringBuilder sb) {
		flatten(node.getExpression(), sb);
		sb.append(".");
		flatten((Expression)node.getName(), sb);
	}

	protected void flatten(InfixExpression node, StringBuilder sb) {
		flatten(node.getLeftOperand(), sb);
		sb.append(' ');
		sb.append(node.getOperator().toString());
		sb.append(' ');
		flatten(node.getRightOperand(), sb);
	}

	protected void flatten(InstanceofExpression node, StringBuilder sb) {
		flatten(node.getLeftOperand(), sb);
		sb.append(" instanceof ");
		flatten(node.getRightOperand(), sb);
	}

	protected void flatten(IntLiteral node, StringBuilder sb) {
		sb.append(node.getNumber());
	}

	protected void flatten(MethodInvocation node, StringBuilder sb) {
		if (node.getExpression() != null) {
			flatten(node.getExpression(), sb);
			sb.append(".");
		}
		if (node.typeArguments().length > 0) {
			sb.append("<");
			doList(node.typeArguments(), sb);
			sb.append(">");
		}
		flatten((Expression)node.getName(), sb);
		sb.append("(");
		doList(node.arguments(), sb);
		sb.append(")");
	}

	@SuppressWarnings("unused")
	protected void flatten(NullLiteral node, StringBuilder sb) {
		sb.append("null");
	}

	protected void flatten(ParenthesizedExpression node, StringBuilder sb) {
		sb.append("(");
		flatten(node.getExpression(), sb);
		sb.append(")");
	}

	protected void flatten(PostfixExpression node, StringBuilder sb) {
		flatten(node.getOperand(), sb);
		sb.append(node.getOperator().toString());
	}

	protected void flatten(PrefixExpression node, StringBuilder sb) {
		sb.append(node.getOperator().toString());
		flatten(node.getOperand(), sb);
	}

	protected void flatten(QualifiedName node, StringBuilder sb) {
		flatten(node.getQualifier(), sb);
		sb.append(".");
		flatten((Expression)node.getName(), sb);
	}

	protected void flatten(SimpleName node, StringBuilder sb) {
		sb.append(node.getIdentifier());
	}

	protected void flatten(StringLiteral node, StringBuilder sb) {
		sb.append(node.getEscapedValue());
	}

	protected void flatten(SuperFieldAccess node, StringBuilder sb) {
		if (node.getQualifier() != null) {
			flatten(node.getQualifier(), sb);
			sb.append(".");
		}
		sb.append("super.");
		flatten((Expression)node.getName(), sb);
	}

	protected void flatten(SuperMethodInvocation node, StringBuilder sb) {
		if (node.getQualifier() != null) {
			flatten(node.getQualifier(), sb);
			sb.append(".");
		}
		sb.append("super.");
		if (node.typeArguments().length > 0) {
			sb.append("<");
			doList(node.typeArguments(), sb);
			sb.append(">");
		}
		flatten((Expression)node.getName(), sb);
		sb.append("(");
		doList(node.arguments(), sb);
		sb.append(")");
	}

	protected void flatten(ThisExpression node, StringBuilder sb) {
		if (node.getQualifier() != null) {
			flatten(node.getQualifier(), sb);
			sb.append(".");
		}
		sb.append("this");
	}

	protected void flatten(TypeLiteral node, StringBuilder sb) {
		flatten(node.getType(), sb);
		sb.append(".class");
	}

	protected void flatten(Assignment node, StringBuilder sb) {
		flatten(node.getLeftHandSide(), sb);
		sb.append(' ');
		sb.append(node.getOperator().toString());
		sb.append(' ');
		flatten(node.getRightHandSide(), sb);
	}

	protected void flatten(ArrayCreation node, StringBuilder sb) {
		sb.append("new ");
		ArrayType at = node.getType();
		flatten(at.getElementType(), sb);
		int dims = at.getDimensions();
		for (Expression dim: node.dimensions()) {
			sb.append('[');
			flatten(dim, sb);
			sb.append(']');
			dims--;
		}
		for (int i= 0; i < dims; i++)
			sb.append("[]");
		flatten(node.getInitializer(), sb);
	}

	protected void flatten(ArrayInitializer node, StringBuilder sb) {
		sb.append('{');
		doList(node.expressions(), sb);
		sb.append('}');
	}
	
	protected void doList(Expression[] children, StringBuilder sb) {
        int iMax = children.length - 1;
        for (int i = 0; ; i++) {
            flatten(children[i], sb);
            if (i == iMax)
                break;
            sb.append(",");
        }
	}

}
