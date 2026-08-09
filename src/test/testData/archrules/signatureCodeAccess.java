import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.slf4j.Logger;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SignatureCodeAccessRules {
    @ArchTest
    static final ArchRule no_logger_failures = noClasses()
            .should().callMethod(Logger.class, "error", java.lang.String.class, java.lang.Throwable.class)
            .orShould().callMethod(Logger.class, "warn", java.lang.String.class, java.lang.Throwable.class)
            .orShould().callMethod(Logger.class, "info", java.lang.String.class, java.lang.Throwable.class);

    @ArchTest
    static final ArchRule no_page_impl_constructors = noClasses()
            .should().callConstructor(PageImpl.class, List.class)
            .orShould().callConstructor(PageImpl.class, List.class, Pageable.class, long.class);
}
