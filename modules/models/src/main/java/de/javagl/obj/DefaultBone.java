package de.javagl.obj;

public class DefaultBone implements Bone {
    private int parent;
    private String name;
    private FloatTuple head;
    private FloatTuple tail;

    public DefaultBone(int parent, String name, FloatTuple head, FloatTuple tail) {
        this.parent = parent;
        this.name = name;
        this.head = head;
        this.tail = tail;
    }

    public DefaultBone(Bone other) {
        this.parent = other.getParent();
        this.name = other.getName();
        this.head = FloatTuples.copy(other.getHead());
        this.tail = FloatTuples.copy(other.getTail());
    }

    @Override
    public int getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FloatTuple getHead() {
        return head;
    }

    @Override
    public FloatTuple getTail() {
        return tail;
    }
}
