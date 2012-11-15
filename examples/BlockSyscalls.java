import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
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


public class BlockSyscalls {

	public static void main(String[] args) {
		//testFile();
		//testExec();
		//testDisable();
		//testOld();
		//testPrint();
	}

	private static void testFile() {
		File f = new File("/tmp/test.txt");
		write(f, "Before");
		read(f);
		MySecurityManager sm = new MySecurityManager();
		System.setSecurityManager(sm);
		write(f, "After");
		read(f);
		delete(f);
		sm.disable();
		write(f, "Super after");
		read(f);
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
			System.out.println("Write failed =)");
		}
	}
	
	private static void read(File f) {
		try {
			BufferedReader out = new BufferedReader(new FileReader(f));
			System.out.println(out.readLine());
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void delete(File f) {
		try {
			f.delete();
			System.out.println("Deleted =(");
			assert false;
		} catch (SecurityException e) {
			System.out.println("Delete failed =)");
		}
	}
	
	private static void testExec() {
		System.setSecurityManager(new MySecurityManager());
		Runtime r = Runtime.getRuntime();
		try {
			r.exec("touch /tmp/a");
			assert false;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			System.out.println("Exec failed =)");
		}
	}
	
	private static void testDisable() {
		MySecurityManager sm = new MySecurityManager();
		System.setSecurityManager(sm);
		try {
			System.setSecurityManager(null);
			assert false;
		} catch (SecurityException e) {
			System.out.println("Disable failed =)");
		}
		sm.disable();
		System.out.println("Disable succeeded =)");
		System.out.println(System.getSecurityManager());
	}
	
	private static void testOld() {
		System.setSecurityManager(new SecurityManager() {
		    @Override
			public void checkPermission(Permission perm) {
		    	if ("setSecurityManager".equals(perm.getName()))
			    	return;
		    	if (perm instanceof FilePermission && "read".equals(perm.getActions()))
			    	throw new MySecurityManager.MySecurityException();
		    	// Do nothing and hence allow anything not explicitly disallowed.
		    }
		});
		File f = new File("/tmp/test.txt");
		try {
			read(f);
			assert false;
		} catch (SecurityException e) {
			System.out.println("Read failed =)");
		}
		System.setSecurityManager(new MySecurityManager());
		try {
			read(f);
			assert false;
		} catch (SecurityException e) {
			System.out.println("Read failed =)");
		}
	}
	
	private static void testPrint() {
		System.setSecurityManager(new MySecurityManager());
		PrinterJob job = PrinterJob.getPrinterJob();
		job.setPrintable(new Printable() {
			@Override
			public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
				return PAGE_EXISTS;
			}
		});
        boolean ok = job.printDialog();
        if (ok) {
            try {
                 job.print();
            } catch (PrinterException ex) {
             /* The job did not successfully complete */
            }
        }
	}
	
	private static class MySecurityManager extends SecurityManager {
		
		private static class MySecurityException extends SecurityException {
		    
		    @Override
			public Throwable fillInStackTrace() {
		    	return this;
		    }
			
		}

		private final SecurityManager oldSecurityManager;
		private boolean disabled;
		
		public MySecurityManager() {
			this.oldSecurityManager = System.getSecurityManager();
			this.disabled = false;
		}
		
		protected void disable() {
			disabled = true;
			System.setSecurityManager(oldSecurityManager);
		}
		
	    @Override
		public void checkPermission(Permission perm) {
	    	if ("setSecurityManager".equals(perm.getName()) && !disabled)
		    	throw new MySecurityException();
	    	if (oldSecurityManager != null)
	    		oldSecurityManager.checkPermission(perm);
	    	// Do nothing and hence allow anything not explicitly disallowed.
	    }

	    @Override
		public void checkWrite(String file) {
	    	throw new MySecurityException();
	    }

	    @Override
		public void checkDelete(String file) {
	    	throw new MySecurityException();
	    }

	    @Override
	    public void checkExec(String cmd) {
	    	throw new MySecurityException();
	    }

	    @Override
	    public void checkPrintJobAccess() {
	    	throw new MySecurityException();
	    }
	}

}
