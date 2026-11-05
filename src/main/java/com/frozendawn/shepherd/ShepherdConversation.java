package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdDialogue.Response;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** One walk through a tree. It knows nothing of players or the world, and refuses any move the node does not offer. */
public final class ShepherdConversation {
    public static final int CONTINUE = -1;

    private final Map<String, Node> nodes;
    private final Predicate<String> holds;
    private final Set<String> flags = new HashSet<>();
    private Node current;
    private int affinityDelta;
    private boolean finished;

    /** {@code holds} answers the file's conditions: this class knows nothing of what they mean. */
    public ShepherdConversation(Map<String, Node> nodes, String start, Predicate<String> holds) {
        this.nodes = nodes;
        this.holds = holds;
        current = arrive(nodes, start, holds);
        if (current == null) throw new IllegalArgumentException("No dialogue node " + start);
        flags.addAll(current.set());
    }

    /** Whether a walk from {@code start} has anywhere to stand. The constructor refuses one that does not. */
    public static boolean opens(Map<String, Node> nodes, String start, Predicate<String> holds) {
        return arrive(nodes, start, holds) != null;
    }

    public Node current() {
        return current;
    }

    /** What she says here, as things stand. */
    public List<Line> lines() {
        return current.lines().stream().filter(line -> line.condition() == null || holds.test(line.condition())).toList();
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
        Node node = arrive(nodes, next, holds);
        if (node == null) {
            finished = true;
            return;
        }
        current = node;
        flags.addAll(node.set());
    }

    /** Where {@code id} really leads, past any node that does not hold. Null if that runs out, or an edited datapack makes it circle. */
    private static Node arrive(Map<String, Node> nodes, String id, Predicate<String> holds) {
        Node node = id == null ? null : nodes.get(id);
        for (int hops = 0; node != null && node.when() != null && !holds.test(node.when()); hops++) {
            if (hops == nodes.size()) return null;
            node = node.otherwise() == null ? null : nodes.get(node.otherwise());
        }
        return node;
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
