package com.edgefort.simulator;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.edgefort.simulator", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule core_must_not_depend_on_providers_admin_or_web = noClasses()
            .that().resideInAPackage("..simulator.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..simulator.provider..", "..simulator.admin..", "..simulator.infrastructure.."
            );

    @ArchTest
    static final ArchRule providers_must_not_depend_on_each_other = SlicesRuleDefinition.slices()
            .matching("com.edgefort.simulator.provider.(*)..")
            .should().notDependOnEachOther();

    @ArchTest
    static final ArchRule providers_must_not_depend_on_admin = noClasses()
            .that().resideInAPackage("..simulator.provider..")
            .should().dependOnClassesThat().resideInAPackage("..simulator.admin..");

    @ArchTest
    static final ArchRule interswitch_transfer_must_not_depend_on_bill_payment = noClasses()
            .that().resideInAPackage("..provider.interswitch.transfer..")
            .should().dependOnClassesThat().resideInAPackage("..provider.interswitch.billpayment..");

    @ArchTest
    static final ArchRule interswitch_bill_payment_must_not_depend_on_transfer = noClasses()
            .that().resideInAPackage("..provider.interswitch.billpayment..")
            .should().dependOnClassesThat().resideInAPackage("..provider.interswitch.transfer..");
}
