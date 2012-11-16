import codehint.CodeHint;
import java.lang.Math;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class Intro {

	public static void main(String[] args) {
		round(423712.137f);
		sum(1, 10);
		inverseSin(1);
		hypotenuse(3, 4);
		getColoredPoint(42, 42, "blue", "evil red");
		findInterfaces(String.class);
		getUppercaseColor(new ColoredPoint(42, 42, "    aqua "));
		getStringInfo("Hello", "world", 1, 2);
	}
	
	private static int round(float d) {
		int rounded = 0;
		System.out.println("Task: round d.");
		return rounded;
	}
	
	private static int sum(int min, int max) {
		int sum = 0;
		for (int i = min; i <= max; i++) {
			for (int j = 1; j <= i; j++) {
				int cur = 0;
				System.out.println("Task: Update cur to by the sum of i and j.");
				sum += cur;
			}
		}
		return sum;
	}
	
	private static double inverseSin(double n) {
		double theta = 0;
		System.out.println("Task: Compute a theta such that sin(theta) == n.");
		return theta;
	}
	
	private static double hypotenuse(double a, double b) {
		double c = 0;
		System.out.println("Task: Synthesize the hypotenuse.");
		return c;
	}
	
	private static Point getColoredPoint(int x, int y, String color, String fakeColor) {
		Point point = null;
		System.out.println("Task: Get a colored point.");
		ColoredPoint coloredPoint = null;
		System.out.println("Task: Get a blue colored point.");
		return point;
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
	
	private static int getStringInfo(String useMe, String ignoreMe, int x, int y) {
		int result = 0;
		System.out.println("Task: Get some integer information from the string.");
		return result;
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
