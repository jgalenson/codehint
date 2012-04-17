import codehint.CodeHint;

public class RedBlackTree {
	
	private static class Node {
		public int val;
		public Node left, right, parent;
	}
	
	private static Node rotateLeft(Node root, Node x) {
		assert(x.right != null);
		Node y = x.right;
		x.right = y.left;
		return y;
	}
	
	private static Node makeNode(int v, Node l, Node r) {
		Node n = new Node();
		n.val = v;
		n.left = l;
		n.right = r;
		n.parent = null;
		return n;
	}
	
	private static Node setParentPointers(Node n, Node parent) {
		if (n != null) {
			n.parent = parent;
			setParentPointers(n.left, n);
			setParentPointers(n.right, n);
		}
		return n;
	}
	
	public static void main(String[] args) {
		//test1();
		test2();
		test3();
	}
	
	private static void test1() {
		Node tree = setParentPointers(makeNode(5, null, makeNode(42, null, makeNode(137, null, null))), null);
		rotateLeft(tree, tree.right);
	}
	
	private static void test2() {
		Node tree = setParentPointers(makeNode(5, null, makeNode(42, null, makeNode(137, makeNode(111, null, null), null))), null);
		rotateLeft(tree, tree.right);
	}
	
	private static void test3() {
		Node tree = setParentPointers(makeNode(5, null, makeNode(13, null, makeNode(42, null, makeNode(137, makeNode(111, null, null), null)))), null);
		rotateLeft(tree, tree.right.right);
	}

}
