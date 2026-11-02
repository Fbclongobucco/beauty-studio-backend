package com.longobuccodev.beautystudio.booking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras da Clean Architecture (padrão App-adm-obras): core não conhece nada; application não conhece infra. */
class LayerDependencyTest {

    private static final String BASE = "com.longobuccodev.beautystudio.booking";
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void coreNaoDependeDeFrameworkNemDeOutrasCamadas() {
        noClasses().that().resideInAPackage(BASE + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                        "tools.jackson..", "com.fasterxml..", "software.amazon..", "com.longobuccodev.beautystudio.messaging..",
                        BASE + ".application..", BASE + ".infra..")
                .check(classes);
    }

    @Test
    void applicationNaoDependeDeFrameworkNemDeInfra() {
        noClasses().that().resideInAPackage(BASE + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                        "tools.jackson..", "com.fasterxml..", "software.amazon..", BASE + ".infra..")
                .check(classes);
    }
}
