package experiments;

import java.awt.Graphics2D;
import org.graphstream.ui.graphicGraph.StyleGroup;
import org.graphstream.ui.swingViewer.basicRenderer.EdgeRenderer;
import org.graphstream.ui.swingViewer.basicRenderer.SwingBasicGraphRenderer;

public class VariableEdgeWidthGraphRenderer extends SwingBasicGraphRenderer {
	protected EdgeRenderer edgeRenderer = new VariableWidthEdgeRenderer();
	
	@Override
	protected void renderGroup(Graphics2D g, StyleGroup group) {
		switch (group.getType()) {
		case NODE:
			nodeRenderer.render(group, g, camera);
			break;
		case EDGE:
			edgeRenderer.render(group, g, camera);
			break;
		case SPRITE:
			spriteRenderer.render(group, g, camera);
			break;
		default:
			// Do nothing
			break;
		}
	}
}
