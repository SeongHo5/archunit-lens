package org.example;

class InvalidCalls extends Outer.ExplicitInner {
    InvalidCalls() { super(""); }
    InvalidCalls(java.lang.String wrong) { wrong.super(""); }

    static void create(java.lang.String wrong) {
        new Outer.ImplicitInner();
        new Outer.ExplicitInner();
        wrong.new ImplicitInner();
        wrong.new ExplicitInner();
        new Outer.ImplicitInner(;
    }
}
