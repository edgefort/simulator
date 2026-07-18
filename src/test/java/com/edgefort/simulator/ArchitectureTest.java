package com.edgefort.simulator;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.edgefort.simulator", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule core_must_not_depend_on_provider_modules = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat().resideInAnyPackage("..provider.nibss..", "..provider.interswitch..");

    @ArchTest
    static final ArchRule nibss_must_not_depend_on_interswitch = noClasses()
            .that().resideInAPackage("..provider.nibss..")
            .should().dependOnClassesThat().resideInAPackage("..provider.interswitch..");

    @ArchTest
    static final ArchRule interswitch_must_not_depend_on_nibss = noClasses()
            .that().resideInAPackage("..provider.interswitch..")
            .should().dependOnClassesThat().resideInAPackage("..provider.nibss..");
}