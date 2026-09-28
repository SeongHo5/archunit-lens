package com.example;

import org.forbidden.Outer.Inner;
import org.forbidden.middle.MiddleApi;
import com.allowed.forbidden;

class SuffixTarget {
    Inner nested;
    MiddleApi subpackage;
    forbidden sameNameAsPackageSegment;
    org.forbidden.ReferenceApi directReference;
}
