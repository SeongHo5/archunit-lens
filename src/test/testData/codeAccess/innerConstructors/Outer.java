package org.example;

public class Outer {
    public class ImplicitInner {}

    public class ExplicitInner {
        public ExplicitInner() { this(""); }
        public ExplicitInner(java.lang.String value) {}
    }

    public class InnerChild extends ExplicitInner {
        public InnerChild() { super(""); }
    }

    public static class StaticNested {
        public StaticNested() { this(""); }
        public StaticNested(java.lang.String value) {}
    }

    void create() {
        new ImplicitInner();
        new ExplicitInner();
        new ExplicitInner("");
        new StaticNested();
        new StaticNested("");
        new TopLevel();
        new TopLevel("");
    }
}

class OuterChild extends Outer {}

class ExternalChild extends Outer.ExplicitInner {
    ExternalChild(Outer outer) { outer.super(""); }
}

class Consumer {
    void create(Outer outer, OuterChild subtype) {
        outer.new ImplicitInner();
        outer.new ExplicitInner();
        outer.new ExplicitInner("");
        subtype.new ExplicitInner();
    }
}

class TopLevel {
    TopLevel() { this(""); }
    TopLevel(java.lang.String value) {}
}
