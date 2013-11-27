package codehint.ast;

public final class PrimitiveType extends Type {
	
	public static class Code {

		private final String name;

		private Code(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	public static final Code INT = new Code("int");
	public static final Code CHAR = new Code("char");
	public static final Code BOOLEAN = new Code("boolean");
	public static final Code SHORT = new Code("short");
	public static final Code LONG = new Code("long");
	public static final Code FLOAT = new Code("float");
	public static final Code DOUBLE = new Code("double");
	public static final Code BYTE = new Code("byte");

	public static final Code VOID = new Code("void");
	
	private final Code code;

	public PrimitiveType(Code code) {
		this.code = code;
	}

	public Code getCode() {
		return code;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return code.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((code == null) ? 0 : code.hashCode());
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
		PrimitiveType other = (PrimitiveType) obj;
		if (code == null) {
			if (other.code != null)
				return false;
		} else if (!code.equals(other.code))
			return false;
		return true;
	}

}
