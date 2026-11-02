package com.longobuccodev.beautystudio.notification.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerDependencyTest {

    private static final String BASE = "com.longobuccodev.beautystudio.notification";
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE);

    @Test
    void coreNaoDependeDeFrameworkNemDeOutrasCamadas() {
        noClasses().that().resideInAPackage(BASE + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                        "tools.jackson..", "com.longobuccodev.beautystudio.messaging..", BASE + ".application..",
                        BASE + ".infra..")
                .check(classes);
    }

    @Test
    void applicationSoUsaOContratoDeEventosSemFramework() {
        noClasses().that().resideInAPackage(BASE + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                        "tools.jackson..", "com.longobuccodev.beautystudio.messaging.infra..", BASE + ".infra..")
                .check(classes);
    }
}
