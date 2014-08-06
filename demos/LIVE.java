import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import codehint.CodeHint;

@SuppressWarnings("unused")
public class LIVE {

	private void configureTree(final JTree jtree) {
		

		
		Dimension size = null;  // What goes here??
		
		layoutOtherElements(size);
		
		
		
		
		
		
		
		
		
		
		
		JMenuBar menuBar = null;  // What goes here??
		
		addMenuItems(menuBar);
		
		
		
		
		
		
		
		
		
		
		jtree.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				JTree tree = jtree;
				int mouseX = e.getX();
				int mouseY = e.getY();
				
				
				
				Object x = null;  // What goes here??
				
				openDirectoryFor(x);
			}
		});
	}
	
	// Setup code below:

	public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            	new LIVE();
            }
        });
	}
	
	public LIVE() {
		JFrame frame = new JFrame("Swing test");
		frame.setPreferredSize(new Dimension(100, 175));
		makeMenu(frame);
		JTree tree = createExampleTree();
		frame.add(tree);
		configureTree(tree);
		showFrame(frame);
	}
	
	private static void makeMenu(final JFrame frame) {
		JMenuBar menuBar = new JMenuBar();
		JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		JMenuItem quit = new JMenuItem("Quit");
		quit.setMnemonic(KeyEvent.VK_Q);
		quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
		quit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.dispose();
				System.exit(0);
			}
		});
		file.add(quit);
		menuBar.add(file);
		frame.setJMenuBar(menuBar);
	}
	
	private static JTree createExampleTree() {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("home");
		top.add(new DefaultMutableTreeNode("Alice"));
		top.add(new DefaultMutableTreeNode("Bob"));
		top.add(new DefaultMutableTreeNode("Eve"));
		JTree tree = new JTree(top);
		return tree;
	}
	
	private static void showFrame(JFrame frame) {
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}

	private void layoutOtherElements(Dimension size) {
		
	}

	private void addMenuItems(JMenuBar menuBar) {
		
	}

	private void showRow(int clickedRow) {
		
	}

	private void openDirectoryFor(Object o) {
		
	}

}
