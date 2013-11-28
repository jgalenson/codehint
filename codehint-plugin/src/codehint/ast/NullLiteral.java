package codehint.ast;

public class NullLiteral extends Expression {
	
	private static NullLiteral nullLiteral = new NullLiteral();
	
	private NullLiteral() {
		super(null);
	}
	
	public static NullLiteral getNullLiteral() {
		return nullLiteral;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return "null";
	}

}
