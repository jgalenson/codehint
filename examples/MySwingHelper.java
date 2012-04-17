import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;


public class MySwingHelper {

	public static JFrame makeFrame(String title) {
		JFrame frame = new JFrame(title);
		frame.setPreferredSize(new Dimension(200, 400));
		return frame;
	}

	public static void showFrame(JFrame frame) {
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}

	public static void resizeLabel(JLabel label, Dimension newDim) {
		label.setPreferredSize(newDim);
	}

	public static JTree createTree() {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("home");
		top.add(new DefaultMutableTreeNode("Alice"));
		top.add(new DefaultMutableTreeNode("Bob"));
		top.add(new DefaultMutableTreeNode("Eve"));
		JTree tree = new JTree(top);
		return tree;
	}

}