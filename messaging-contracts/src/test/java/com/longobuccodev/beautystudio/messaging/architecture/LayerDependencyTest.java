package com.longobuccodev.beautystudio.messaging.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerDependencyTest {

    private static final String BASE = "com.longobuccodev.beautystudio.messaging";
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void coreNaoDependeDeFrameworkNemDeOutrasCamadas() {
        noClasses().that().resideInAPackage(BASE + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "tools.jackson..", "com.fasterxml..", "com.rabbitmq..",
                        BASE + ".application..", BASE + ".infra..")
                .check(classes);
    }

    @Test
    void applicationNaoDependeDeFrameworkNemDeInfra() {
        noClasses().that().resideInAPackage(BASE + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "tools.jackson..", "com.fasterxml..", "com.rabbitmq..",
                        BASE + ".infra..")
                .check(classes);
    }
}
