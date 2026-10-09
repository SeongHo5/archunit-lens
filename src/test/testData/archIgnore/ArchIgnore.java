package com.tngtech.archunit.junit;

public @interface ArchIgnore {
    String reason() default "";
}
