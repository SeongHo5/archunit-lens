import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRules {
    @ArchTest
    static final ArchRule spring_controllers = classes().that().resideInAPackage("..controller..")
            .should().haveSimpleNameEndingWith("Controller")
            .andShould().beAnnotatedWith("org.springframework.stereotype.Controller");

    @ArchTest
    static final ArchRule mapstruct_converters = classes().that().haveSimpleNameEndingWith("Converter")
            .should().beInterfaces()
            .andShould().beAnnotatedWith("org.mapstruct.Mapper");

    @ArchTest
    static final ArchRule mybatis_mappers = classes().that().resideInAPackage("..mapper..")
            .should().beInterfaces()
            .andShould().haveSimpleNameEndingWith("Mapper")
            .andShould().beAnnotatedWith("org.apache.ibatis.annotations.Mapper");
}
