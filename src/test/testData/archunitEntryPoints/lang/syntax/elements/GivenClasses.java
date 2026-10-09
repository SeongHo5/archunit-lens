package com.tngtech.archunit.lang.syntax.elements;

import com.tngtech.archunit.lang.ArchRule;

public interface GivenClasses {
    GivenClasses should();
    ArchRule beInterfaces();
}
