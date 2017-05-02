package experiments;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import org.graphstream.graph.Element;
import org.graphstream.ui.graphicGraph.GraphicEdge;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicNode;
import org.graphstream.ui.graphicGraph.StyleGroup;
import org.graphstream.ui.graphicGraph.StyleGroup.ElementEvents;
import org.graphstream.ui.swingViewer.basicRenderer.EdgeRenderer;
import org.graphstream.ui.view.Camera;

public class VariableWidthEdgeRenderer extends EdgeRenderer {
	@Override
	protected void renderElement(StyleGroup group, Graphics2D g, Camera camera,
			GraphicElement element) {
		GraphicEdge edge = (GraphicEdge) element;
		GraphicNode node0 = (GraphicNode) edge.getNode0();
		GraphicNode node1 = (GraphicNode) edge.getNode1();

		shape.setLine(node0.x, node0.y, node1.x, node1.y);
		Stroke defaultStroke = g.getStroke();
		System.out.println(edge.getAttributeCount());
		if(edge.hasAttribute("width"))
			g.setStroke(new BasicStroke((float)edge.getAttribute("width"), BasicStroke.CAP_BUTT,
				BasicStroke.JOIN_BEVEL));
		g.draw(shape);
		g.setStroke(defaultStroke);
		renderArrow(group, g, camera, edge);
		renderText(group, g, camera, element);
	}
	
	public void render(StyleGroup group, Graphics2D g, Camera camera) {
		setupRenderingPass(group, g, camera);
		pushStyle(group, g, camera);

		for (Element e : group.bulkElements()) {
			GraphicElement ge = (GraphicElement) e;

			if (camera.isVisible(ge))
				renderElement(group, g, camera, ge);
			else
				elementInvisible(group, g, camera, ge);
		}

		if (group.hasDynamicElements()) {
			for (Element e : group.dynamicElements()) {
				GraphicElement ge = (GraphicElement) e;

				if (camera.isVisible(ge)) {
					if (!group.elementHasEvents(ge)) {
						pushDynStyle(group, g, camera, ge);
						renderElement(group, g, camera, ge);
					}
				} else {
					elementInvisible(group, g, camera, ge);
				}
			}
		}

		if (group.hasEventElements()) {
			for (ElementEvents event : group.elementsEvents()) {
				GraphicElement ge = (GraphicElement) event.getElement();

				if (camera.isVisible(ge)) {
					event.activate();
					pushStyle(group, g, camera);
					renderElement(group, g, camera, ge);
					event.deactivate();
				} else {
					elementInvisible(group, g, camera, ge);
				}
			}

			hadEvents = true;
		} else {
			hadEvents = false;
		}
	}

}
