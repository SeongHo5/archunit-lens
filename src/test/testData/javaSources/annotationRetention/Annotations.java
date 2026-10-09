package com.example;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
@Retention(RetentionPolicy.RUNTIME) @interface Marker {}
@Retention(RetentionPolicy.SOURCE) @Marker @interface MiddleSource {}
@Retention(RetentionPolicy.SOURCE) @MiddleSource @interface SourceViaSource {}
@SourceViaSource class SourceViaSourceTarget { @SourceViaSource int field; @SourceViaSource SourceViaSourceTarget() {} @SourceViaSource void method() {} }
@Retention(RetentionPolicy.CLASS) @MiddleSource @interface ClassViaSource {}
@ClassViaSource class ClassViaSourceTarget { @ClassViaSource int field; @ClassViaSource ClassViaSourceTarget() {} @ClassViaSource void method() {} }
@Retention(RetentionPolicy.RUNTIME) @MiddleSource @interface RuntimeViaSource {}
@RuntimeViaSource class RuntimeViaSourceTarget { @RuntimeViaSource int field; @RuntimeViaSource RuntimeViaSourceTarget() {} @RuntimeViaSource void method() {} }
@MiddleSource @interface DefaultViaSource {}
@DefaultViaSource class DefaultViaSourceTarget { @DefaultViaSource int field; @DefaultViaSource DefaultViaSourceTarget() {} @DefaultViaSource void method() {} }
@Retention(RetentionPolicy.CLASS) @Marker @interface MiddleClass {}
@Retention(RetentionPolicy.SOURCE) @MiddleClass @interface SourceViaClass {}
@SourceViaClass class SourceViaClassTarget { @SourceViaClass int field; @SourceViaClass SourceViaClassTarget() {} @SourceViaClass void method() {} }
@Retention(RetentionPolicy.CLASS) @MiddleClass @interface ClassViaClass {}
@ClassViaClass class ClassViaClassTarget { @ClassViaClass int field; @ClassViaClass ClassViaClassTarget() {} @ClassViaClass void method() {} }
@Retention(RetentionPolicy.RUNTIME) @MiddleClass @interface RuntimeViaClass {}
@RuntimeViaClass class RuntimeViaClassTarget { @RuntimeViaClass int field; @RuntimeViaClass RuntimeViaClassTarget() {} @RuntimeViaClass void method() {} }
@MiddleClass @interface DefaultViaClass {}
@DefaultViaClass class DefaultViaClassTarget { @DefaultViaClass int field; @DefaultViaClass DefaultViaClassTarget() {} @DefaultViaClass void method() {} }
@Retention(RetentionPolicy.RUNTIME) @Marker @interface MiddleRuntime {}
@Retention(RetentionPolicy.SOURCE) @MiddleRuntime @interface SourceViaRuntime {}
@SourceViaRuntime class SourceViaRuntimeTarget { @SourceViaRuntime int field; @SourceViaRuntime SourceViaRuntimeTarget() {} @SourceViaRuntime void method() {} }
@Retention(RetentionPolicy.CLASS) @MiddleRuntime @interface ClassViaRuntime {}
@ClassViaRuntime class ClassViaRuntimeTarget { @ClassViaRuntime int field; @ClassViaRuntime ClassViaRuntimeTarget() {} @ClassViaRuntime void method() {} }
@Retention(RetentionPolicy.RUNTIME) @MiddleRuntime @interface RuntimeViaRuntime {}
@RuntimeViaRuntime class RuntimeViaRuntimeTarget { @RuntimeViaRuntime int field; @RuntimeViaRuntime RuntimeViaRuntimeTarget() {} @RuntimeViaRuntime void method() {} }
@MiddleRuntime @interface DefaultViaRuntime {}
@DefaultViaRuntime class DefaultViaRuntimeTarget { @DefaultViaRuntime int field; @DefaultViaRuntime DefaultViaRuntimeTarget() {} @DefaultViaRuntime void method() {} }
@Marker @interface MiddleDefault {}
@Retention(RetentionPolicy.SOURCE) @MiddleDefault @interface SourceViaDefault {}
@SourceViaDefault class SourceViaDefaultTarget { @SourceViaDefault int field; @SourceViaDefault SourceViaDefaultTarget() {} @SourceViaDefault void method() {} }
@Retention(RetentionPolicy.CLASS) @MiddleDefault @interface ClassViaDefault {}
@ClassViaDefault class ClassViaDefaultTarget { @ClassViaDefault int field; @ClassViaDefault ClassViaDefaultTarget() {} @ClassViaDefault void method() {} }
@Retention(RetentionPolicy.RUNTIME) @MiddleDefault @interface RuntimeViaDefault {}
@RuntimeViaDefault class RuntimeViaDefaultTarget { @RuntimeViaDefault int field; @RuntimeViaDefault RuntimeViaDefaultTarget() {} @RuntimeViaDefault void method() {} }
@MiddleDefault @interface DefaultViaDefault {}
@DefaultViaDefault class DefaultViaDefaultTarget { @DefaultViaDefault int field; @DefaultViaDefault DefaultViaDefaultTarget() {} @DefaultViaDefault void method() {} }
@Marker class DirectTarget { @Marker int field; @Marker DirectTarget() {} @Marker void method() {} }
@Retention(RetentionPolicy.SOURCE) @interface SourceMarker {}
@SourceMarker @interface ToSourceMarker {}
@ToSourceMarker class SourceTarget { @ToSourceMarker int field; @ToSourceMarker SourceTarget() {} @ToSourceMarker void method() {} }
@CycleB @interface CycleA {}
@CycleA @Marker @interface CycleB {}
@CycleA class CycleTarget { @CycleA int field; @CycleA CycleTarget() {} @CycleA void method() {} }
@UnrelatedCycleB @interface UnrelatedCycleA {}
@UnrelatedCycleA @interface UnrelatedCycleB {}
@UnrelatedCycleA class UnrelatedCycleTarget { @UnrelatedCycleA int field; @UnrelatedCycleA UnrelatedCycleTarget() {} @UnrelatedCycleA void method() {} }
@Retention(RetentionPolicy.SOURCE) @SourceCycleB @interface SourceCycleA {}
@SourceCycleA @Marker @interface SourceCycleB {}
@SourceCycleA class SourceCycleTarget { @SourceCycleA int field; @SourceCycleA SourceCycleTarget() {} @SourceCycleA void method() {} }
@com.example.missing.Unresolved @interface UnresolvedPath {}
@UnresolvedPath class UnresolvedTarget { @UnresolvedPath int field; @UnresolvedPath UnresolvedTarget() {} @UnresolvedPath void method() {} }
@Retention(RetentionPolicy.SOURCE) @com.example.missing.Unresolved @interface SourceUnresolvedPath {}
@SourceUnresolvedPath class SourceUnresolvedTarget { @SourceUnresolvedPath int field; @SourceUnresolvedPath SourceUnresolvedTarget() {} @SourceUnresolvedPath void method() {} }
@Retention(RetentionPolicy.UNKNOWN) @Marker @interface UnresolvedRetention {}
@UnresolvedRetention class UnresolvedRetentionTarget { @UnresolvedRetention int field; @UnresolvedRetention UnresolvedRetentionTarget() {} @UnresolvedRetention void method() {} }
@Retention @Marker @interface MissingRetentionValue {}
@MissingRetentionValue class MissingRetentionValueTarget { @MissingRetentionValue int field; @MissingRetentionValue MissingRetentionValueTarget() {} @MissingRetentionValue void method() {} }
@Retention(RetentionPolicy.SOURCE) @Marker @interface SourceBranch {}
@SourceBranch @Marker @interface RetainedBranch {}
@RetainedBranch class RetainedBranchTarget { @RetainedBranch int field; @RetainedBranch RetainedBranchTarget() {} @RetainedBranch void method() {} }
@UnresolvedPath @Marker @interface ResolvedBranch {}
@ResolvedBranch class ResolvedBranchTarget { @ResolvedBranch int field; @ResolvedBranch ResolvedBranchTarget() {} @ResolvedBranch void method() {} }
@Retention(java.lang.annotation.RetentionPolicy.SOURCE) @Marker @interface QualifiedSourcePath {}
@QualifiedSourcePath class QualifiedSourceTarget { @QualifiedSourcePath int field; @QualifiedSourcePath QualifiedSourceTarget() {} @QualifiedSourcePath void method() {} }
class OtherRetention { @interface Retention { String value(); } }
@OtherRetention.Retention("SOURCE") @Marker @interface CustomRetentionPath {}
@CustomRetentionPath class CustomRetentionTarget { @CustomRetentionPath int field; @CustomRetentionPath CustomRetentionTarget() {} @CustomRetentionPath void method() {} }
