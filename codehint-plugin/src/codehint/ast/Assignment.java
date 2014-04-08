package codehint.ast;

public class Assignment extends Expression {
	
	public static class Operator {
		
		private final String token;
		
		private Operator(String token) {
			this.token = token;
		}
		
		@Override
		public String toString() {
			return token;
		}

		public static final Operator ASSIGN = new Operator("=");
		public static final Operator PLUS_ASSIGN = new Operator("+=");
		public static final Operator MINUS_ASSIGN = new Operator("-=");
		public static final Operator TIMES_ASSIGN = new Operator("*=");
		public static final Operator DIVIDE_ASSIGN = new Operator("/=");
		public static final Operator BIT_AND_ASSIGN = new Operator("&=");
		public static final Operator BIT_OR_ASSIGN = new Operator("|=");
		public static final Operator BIT_XOR_ASSIGN = new Operator("^=");
		public static final Operator REMAINDER_ASSIGN = new Operator("%=");
		public static final Operator LEFT_SHIFT_ASSIGN = new Operator("<<=");
		public static final Operator RIGHT_SHIFT_SIGNED_ASSIGN = new Operator(">>=");
		public static final Operator RIGHT_SHIFT_UNSIGNED_ASSIGN = new Operator(">>>=");

		
	}
	
	private final Expression lhs;
	private final Operator operator; 
	private final Expression rhs;
	
	public Assignment(Expression lhs, Operator operator, Expression rhs) {
		super(lhs.getStaticType());
		this.lhs = lhs;
		this.operator = operator;
		this.rhs = rhs;
	}

	public Expression getLeftHandSide() {
		return lhs;
	}

	public Operator getOperator() {
		return operator;
	}

	public Expression getRightHandSide() {
		return rhs;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, lhs);
			acceptChild(visitor, rhs);
		}
	}
	
	@Override
	public String toString() {
		return lhs.toString() + operator.toString() + rhs.toString(); 
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lhs == null) ? 0 : lhs.hashCode());
		result = prime * result
				+ ((operator == null) ? 0 : operator.hashCode());
		result = prime * result + ((rhs == null) ? 0 : rhs.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Assignment other = (Assignment) obj;
		if (lhs == null) {
			if (other.lhs != null)
				return false;
		} else if (!lhs.equals(other.lhs))
			return false;
		if (operator == null) {
			if (other.operator != null)
				return false;
		} else if (!operator.equals(other.operator))
			return false;
		if (rhs == null) {
			if (other.rhs != null)
				return false;
		} else if (!rhs.equals(other.rhs))
			return false;
		return true;
	}

}
