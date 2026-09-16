package com.fanli.sakurazakatranslator.capture;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Reads a bounded node snapshot. Owns no UI, requests, or long-lived Android node references. */
public final class NodeTreeReader {
    private static final int MAX_DEPTH = 80;
    private static final int MAX_NODES = 800;

    public NodeReport read(AccessibilityNodeInfo root) {
        Traversal traversal = new Traversal();
        traversal.visit(root, 0, null, 0);
        return new NodeReport(traversal.nodes, traversal.truncated);
    }

    public static final class NodeReport {
        public final List<NodeRecord> nodes;
        public final List<TextFragment> fragments;
        public final int visitedNodes, textBlocks;
        public final boolean truncated;

        private NodeReport(List<NodeRecord> nodes, boolean truncated) {
            this.nodes = List.copyOf(nodes);
            this.fragments = List.copyOf(TextAssembly.fromNodes(nodes));
            this.visitedNodes = nodes.size();
            this.textBlocks = fragments.size();
            this.truncated = truncated;
        }

        public String displayText() {
            String text = TextAssembly.formatNodeSections(fragments);
            return truncated ? text + "\n[节点遍历达到深度或数量上限，结果可能截断]" : text;
        }
    }

    private static final class Traversal {
        final List<NodeRecord> nodes = new ArrayList<>();
        boolean truncated;

        void visit(AccessibilityNodeInfo node, int depth, String parentId, int childIndex) {
            if (node == null) return;
            if (depth > MAX_DEPTH || nodes.size() >= MAX_NODES) {
                truncated = true;
                return;
            }
            int index = nodes.size();
            String id = "n" + (index + 1);
            Rect screen = new Rect();
            Rect window = new Rect();
            node.getBoundsInScreen(screen);
            node.getBoundsInWindow(window);
            nodes.add(new NodeRecord(id, parentId, childIndex, index, node.getWindowId(),
                    raw(node.getText()), raw(node.getContentDescription()), raw(node.getClassName()),
                    node.getViewIdResourceName(), bounds(screen), bounds(window),
                    node.isVisibleToUser(), node.isClickable(), node.getCollectionItemInfo() != null));
            int childCount = node.getChildCount();
            for (int child = 0; child < childCount; child++) {
                visit(node.getChild(child), depth + 1, id, child);
                if (truncated) return;
            }
            // minSdk 34: node.recycle() is a no-op. Keep references scoped to this traversal.
        }
    }

    private static String raw(CharSequence value) { return value == null ? null : value.toString(); }
    private static Bounds bounds(Rect value) {
        return new Bounds(value.left, value.top, value.right, value.bottom);
    }
}
