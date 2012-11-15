import java.awt.print.PrinterJob;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Permission;

import codehint.CodeHint;
import codehint.CodeHintImpl;

@SuppressWarnings("unused")
public class ExternalEffects {

	public static void main(String[] args) {
		//testFile();
		//testExec();
		//testDisable();
		testOld();
		//testPrint();
	}

	private static void testFile() {
		File f = new File("/tmp/delete_me");
		write (f, "Hello");
		boolean b = false;
		System.out.println(b);
	}
	
	private static void write(File f, String s) {
		try {
			BufferedWriter out = new BufferedWriter(new PrintWriter(f));
			out.write(s);
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			System.out.println("Write failed");
		}
	}
	
	private static String read(File f) {
		try {
			BufferedReader out = new BufferedReader(new FileReader(f));
			String line = out.readLine();
			out.close();
			return line;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private static void testExec() {
		Runtime r = Runtime.getRuntime();
		String s = "touch /tmp/a";
		int n = 0;
		Object o = null;
		System.out.println("Hi");
	}
	
	private static void testDisable() {
		File f = new File("/tmp/delete_me");
		Object o = null;
		System.out.println("Hi");
	}
	
	private static Object disableSecurityManager(Object x) {
		System.setSecurityManager(null);
		return x;
	}
	
	private static Object delete(File f) {
		f.delete();
		return f;
	}
	
	private static void testOld() {
		System.setSecurityManager(new SecurityManager() {
		    @Override
			public void checkPermission(Permission perm) {
		    	if (perm instanceof FilePermission && "read".equals(perm.getActions()) && "/tmp/test.txt".equals(perm.getName()))
			    	throw new SecurityException();
		    	// Do nothing and hence allow anything not explicitly disallowed.
		    }
		});
		File f = new File("/tmp/test.txt");
		String contents = null;
		System.out.println(contents);
	}
	
	private static void testPrint() {
		PrinterJob job = null;
		System.out.println(job);
	}

}
