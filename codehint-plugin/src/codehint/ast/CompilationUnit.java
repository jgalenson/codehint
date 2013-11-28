package codehint.ast;

import org.eclipse.jdt.core.compiler.IProblem;

public class CompilationUnit extends ASTNode {
	
	private final IProblem[] problems;

	public CompilationUnit(IProblem[] problems) {
		this.problems = problems;
	}

	public IProblem[] getProblems() {
		return problems;
	}

	@Override
	protected void accept0(ASTVisitor visitor) {
	}

}
