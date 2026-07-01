import com.example.QueryMapper;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.example.persistence")
class ArchitectureRules {
    @ArchTest
    static final ArchRule query_mappers_should_be_interfaces =
            classes()
                    .that()
                    .haveSimpleNameEndingWith("QueryMapper")
                    .should()
                    .beInterfaces()
                    .andShould()
                    .beAssignableTo(QueryMapper.class);
}
