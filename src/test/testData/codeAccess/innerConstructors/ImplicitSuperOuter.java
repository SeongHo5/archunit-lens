package org.example;

public class Outer {
    public class InnerParent {}
    public class NestedChild extends InnerParent {
        public NestedChild() { super(); }
    }
}

class OuterChild extends Outer {}
class ExternalChild extends Outer.InnerParent {
    ExternalChild() { super(); }
    ExternalChild(Outer outer) { outer.super(); }
    ExternalChild(OuterChild outer) { outer.super(); }
    ExternalChild(java.lang.String wrong) { wrong.super(); }
}
