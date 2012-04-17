import codehint.CodeHint;
import java.lang.Math;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class Intro {

	public static void main(String[] args) {
		round((float)42.137);
		sum(1, 10);
		inverseSin(1);
		hypotenuse(3, 4);
		getBluePoint(new Point(42, 42), new ColoredPoint(42, 42, "blue"), new ColoredPoint(42, 42, "green"));
		findInterfaces(String.class);
		getUppercaseColor(new ColoredPoint(42, 42, "aqua "));
	}
	
	private static int round(float d) {
		int rounded = -1;
		System.out.println("Task: round d.");
		return rounded;
	}
	
	private static int sum(int l, int u) {
		int sum = 0;
		for (int i = l; i <= u; i++) {
			System.out.println("Task: Update sum to keep a running sum of the numbers between l and i.");
		}
		return sum;
	}
	
	private static double inverseSin(double n) {
		double theta = -1;
		System.out.println("Task: Compute a theta such that sin(theta) == n.");
		return theta;
	}
	
	private static double hypotenuse(double a, double b) {
		double c = -1;
		System.out.println("Task: Synthesize the hypotenuse.");
		return c;
	}
	
	private static Point getBluePoint(Point p1, ColoredPoint p2, Point p3) {
		Point bluePoint = null;
		System.out.println("Task: Get the blue point.");
		return bluePoint;
	}
	
	private static Type[] findInterfaces(Class<String> c) {
		Type[] result = null;
		System.out.println("Find all the interfaces implemented by the given class.");
		return result;
	}
	
	private static String getUppercaseColor(ColoredPoint p) {
		String uppercaseColor = null;
		System.out.println("Task: Get an upper case string of p's color with any surrounding whitespace removed.");
		return uppercaseColor;
	}
	
	private static class Point {
		private int x, y;
		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
		public int getX() {
			return x;
		}
		public int getY() {
			return x;
		}
		public String toString() {
			return "(" + x + "," + y + ")";
		}
	}
	
	private static class ColoredPoint extends Point {
		private String color;
		public ColoredPoint(int x, int y, String color) {
			super(x, y);
			this.color = color;
		}
		public String getColor() {
			return color;
		}
		public String toString() {
			return super.toString() + "@" + color;
		}
	}

}
