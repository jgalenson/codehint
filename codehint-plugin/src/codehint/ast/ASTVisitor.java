package codehint.ast;

@SuppressWarnings("unused")
public class ASTVisitor {

	public boolean preVisit(ASTNode node) {
		return true;
	}

	public void postVisit(ASTNode node) {

	}
	
	public boolean visit(ArrayAccess node) {
		return true;
	}
	
	public boolean visit(ArrayCreation node) {
		return true;
	}
	
	public boolean visit(ArrayInitializer node) {
		return true;
	}
	
	public boolean visit(ArrayType node) {
		return true;
	}
	
	public boolean visit(Assignment node) {
		return true;
	}
	
	public boolean visit(BooleanLiteral node) {
		return true;
	}
	
	public boolean visit(CastExpression node) {
		return true;
	}
	
	public boolean visit(CharacterLiteral node) {
		return true;
	}
	
	public boolean visit(ClassInstanceCreation node) {
		return true;
	}
	
	public boolean visit(ConditionalExpression node) {
		return true;
	}
	
	public boolean visit(DoubleLiteral node) {
		return true;
	}
	
	public boolean visit(FieldAccess node) {
		return true;
	}
	
	public boolean visit(FloatLiteral node) {
		return true;
	}
	
	public boolean visit(InfixExpression node) {
		return true;
	}

	public boolean visit(InstanceofExpression node) {
		return true;
	}
	
	public boolean visit(IntLiteral node) {
		return true;
	}
	
	public boolean visit(LongLiteral node) {
		return true;
	}
	
	public boolean visit(MethodInvocation node) {
		return true;
	}
	
	public boolean visit(NullLiteral node) {
		return true;
	}
	
	public boolean visit(ParameterizedType node) {
		return true;
	}
	
	public boolean visit(ParenthesizedExpression node) {
		return true;
	}
	
	public boolean visit(PostfixExpression node) {
		return true;
	}
	
	public boolean visit(PrefixExpression node) {
		return true;
	}
	
	public boolean visit(PrimitiveType node) {
		return true;
	}
	
	public boolean visit(QualifiedName node) {
		return true;
	}
	
	public boolean visit(QualifiedType node) {
		return true;
	}
	
	public boolean visit(SimpleName node) {
		return true;
	}
	
	public boolean visit(SimpleType node) {
		return true;
	}
	
	public boolean visit(StringLiteral node) {
		return true;
	}
	
	public boolean visit(SuperFieldAccess node) {
		return true;
	}
	
	public boolean visit(SuperMethodInvocation node) {
		return true;
	}
	
	public boolean visit(ThisExpression node) {
		return true;
	}
	
	public boolean visit(TypeLiteral node) {
		return true;
	}

}
