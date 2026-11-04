package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdDialogue.Response;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One walk through a tree. It knows nothing of players or the world, and refuses any move the node does not offer. */
public final class ShepherdConversation {
    public static final int CONTINUE = -1;

    private final Map<String, Node> nodes;
    private final Set<String> flags = new HashSet<>();
    private Node current;
    private int affinityDelta;
    private boolean finished;

    public ShepherdConversation(Map<String, Node> nodes, String start) {
        this.nodes = nodes;
        current = nodes.get(start);
        if (current == null) throw new IllegalArgumentException("No dialogue node " + start);
        flags.addAll(current.set());
    }

    public Node current() {
        return current;
    }

    /** A slot's ordinal, or {@link #CONTINUE}. False, with nothing changed, on anything the node does not offer. */
    public boolean answer(int slotOrContinue) {
        if (finished) return false;
        if (slotOrContinue == CONTINUE) {
            if (!current.responses().isEmpty()) return false;
            move(current.next());
            return true;
        }
        for (Response response : current.responses()) {
            if (response.slot().ordinal() != slotOrContinue) continue;
            affinityDelta += response.affinity();
            flags.addAll(response.set());
            move(response.next());
            return true;
        }
        return false;
    }

    /** A next that has gone missing, say from an edited datapack, ends the talk instead of stranding it. */
    private void move(String next) {
        Node node = next == null ? null : nodes.get(next);
        if (node == null) {
            finished = true;
            return;
        }
        current = node;
        flags.addAll(node.set());
    }

    public int affinityDelta() {
        return affinityDelta;
    }

    public Set<String> flags() {
        return Set.copyOf(flags);
    }

    public boolean finished() {
        return finished;
    }
}
