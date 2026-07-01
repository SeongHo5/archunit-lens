import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "io.indoorplus")
class ArchitectureRules {
    @ArchTest
    static final ArchRule mapper_annotation_must_be_exclusive =
            classes()
                    .that()
                    .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                    .should()
                    .notBeAnnotatedWith("io.indoorplus.SecondaryMapper")
                    .because("Primary and secondary mapper annotations must be exclusive.");
}
