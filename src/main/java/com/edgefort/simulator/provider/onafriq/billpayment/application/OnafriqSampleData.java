package com.edgefort.simulator.provider.onafriq.billpayment.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Deterministic {@code data} payloads for Onafriq catalog, lookup, and validation operations. Every payload copies the
 * field names and sample values of the published examples; where the documentation publishes no example the closest
 * documented shape is reused and recorded in ONAFRIQ_BILL_PAYMENT_V1.md.
 */
public final class OnafriqSampleData {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final JsonNode BALANCE = parse("""
            {"balance": 823780.1, "lastDeposit": "2019-11-28T14:16:45.97+01:00"}
            """);

    private static final List<Service> SERVICES = List.of(
            new Service("airtime", "mtn", "MTN VTU"),
            new Service("airtime", "airtel", "AIRTEL VTU"),
            new Service("airtime", "glo", "GLO VTU"),
            new Service("airtime", "9mobile", "9 MOBILE VTU"),
            new Service("airtime", "smile", "SMILE (TOP-UP)"),
            new Service("databundle", "mtn", "MTN Data Bundle"),
            new Service("databundle", "airtel", "AIRTEL Data Bundle"),
            new Service("databundle", "glo", "GLO Data Bundle"),
            new Service("databundle", "9mobile", "9-Mobile Data Bundle"),
            new Service("databundle", "smile", "SMILE Communications Data Bundle"),
            new Service("epin", "spectranet", "SPECTRANET Internet Provider (e-Pin)"),
            new Service("epin", "glo", "GLO Airtime Recharge (ePin)"),
            new Service("epin", "9mobile", "9-MOBILE Airtime Recharge (ePin)"),
            new Service("epin", "waec", "WAEC Result Checker (ePin)"),
            new Service("epin", "bulksms", "Click Bulk SMS (ePin)"),
            new Service("cabletv", "dstv", "Multichoice DSTV"),
            new Service("cabletv", "gotv", "Multichoice GOTV"),
            new Service("cabletv", "startimes", "Startimes"),
            new Service("electricity", "ikeja_electric_prepaid", "IKEJA Electricity Distribution Company PREPAID"),
            new Service("electricity", "ikeja_electric_postpaid", "IKEJA Electricity Distribution Company POSTPAID"),
            new Service("electricity", "eko_electric_prepaid", "EKO Electricity Distribution Company PREPAID"),
            new Service("electricity", "eko_electric_postpaid", "EKO Electricity Distribution Company POSTPAID"),
            new Service("betting", "bet9ja", "Bet9ja"),
            new Service("betting", "betking", "Betking"),
            new Service("betting", "betway", "Betway")
    );

    private static final JsonNode CATEGORIES = parse("""
            [
              {"name": "Airtime", "service_type": "airtime"},
              {"name": "Data Bundle", "service_type": "databundle"},
              {"name": "E-Pin", "service_type": "epin"},
              {"name": "Cable TV", "service_type": "cabletv"},
              {"name": "Electricity", "service_type": "electricity"},
              {"name": "Betting", "service_type": "betting"}
            ]
            """);

    private static final JsonNode AIRTIME_PROVIDERS = parse("""
            {"providers": [
              {"service_type": "mtn", "shortname": "mtn", "name": "Mtn Nigeria", "plans": ["prepaid"]},
              {"service_type": "airtel", "shortname": "airtel", "name": "Airtel Nigeria", "plans": ["prepaid"]},
              {"service_type": "glo", "shortname": "glo", "name": "Globacom Nigeria", "plans": ["prepaid"]},
              {"service_type": "9mobile", "shortname": "9mobile", "name": "9 Mobile NG", "plans": ["prepaid"]},
              {"service_type": "smile", "shortname": "smile", "name": "Smile Comm. NG", "plans": ["prepaid"]}
            ]}
            """);

    private static final JsonNode DATA_BUNDLE_PROVIDERS = parse("""
            {"providers": [
              {"name": "Mtn", "service_type": "mtn"},
              {"name": "Glo", "service_type": "glo"},
              {"name": "Airtel", "service_type": "airtel"},
              {"name": "9Mobile", "service_type": "9mobile"},
              {"name": "Smile", "service_type": "smile"},
              {"name": "Dstv Showmax", "service_type": "dstvshowmax"},
              {"name": "Spectranet", "service_type": "spectranet"}
            ]}
            """);

    private static final JsonNode CABLE_TV_PROVIDERS = parse("""
            {"providers": [
              {"name": "DSTV", "service_type": "dstv"},
              {"name": "GOTV", "service_type": "gotv"},
              {"name": "STARTIMES", "service_type": "startimes"}
            ]}
            """);

    private static final JsonNode EPIN_PROVIDERS = parse("""
            {"providers": [
              {"name": "9 Mobile", "service_type": "9mobile"},
              {"name": "Glo Nigeria", "service_type": "glo"},
              {"name": "WAEC", "service_type": "waec"},
              {"name": "Bulk Sms", "service_type": "bulksms"},
              {"name": "Spectranet Nigeria", "service_type": "spectranet"}
            ]}
            """);

    private static final JsonNode BETTING_BILLERS = parse("""
            {"providers": [
              {"name": "Bet9ja", "service_type": "bet9ja"},
              {"name": "Betking", "service_type": "betking"},
              {"name": "Betway", "service_type": "betway"}
            ]}
            """);

    private static final JsonNode ELECTRICITY_BILLERS = parse("""
            [
              {"name": "Ikeja Electricity Dist.Company (IEDC)",
               "service_types": {"ikeja_electric_prepaid": "PREPAID", "ikeja_electric_postpaid": "POSTPAID"}},
              {"name": "Ibadan Electricity Dist.Company (IBEDC)",
               "service_types": {"ibadan_electric_prepaid": "PREPAID", "ibadan_electric_postpaid": "POSTPAID"}},
              {"name": "Eko Electricity Dist.Company (EKEDC)",
               "service_types": {"eko_electric_prepaid": "PREPAID", "eko_electric_postpaid": "POSTPAID"}},
              {"name": "Kano Electricity Dist.Company (KEDC)",
               "service_types": {"kedco_electric_prepaid": "PREPAID", "kedco_electric_postpaid": "POSTPAID"}},
              {"name": "Abuja Electricity Dist.Company (AEDC)",
               "service_types": {"abuja_electric_prepaid": "PREPAID", "abuja_electric_postpaid": "POSTPAID"}}
            ]
            """);

    private static final JsonNode DATA_BUNDLES = parse("""
            [
              {"name": "30MB for 1 day", "allowance": "30MB", "price": 100, "validity": "1 day", "datacode": 100},
              {"name": "100MB for 1 day", "allowance": "100MB", "price": 200, "validity": "1 day", "datacode": 200},
              {"name": "750MB for 7 days", "allowance": "750MB", "price": 500, "validity": "7 days", "datacode": 500},
              {"name": "1.5GB for 30 days", "allowance": "1.5GB", "price": 1000, "validity": "30 days",
               "datacode": 1000},
              {"name": "3.5GB for 30 days", "allowance": "3.5GB", "price": 2000, "validity": "30 days",
               "datacode": 2000},
              {"name": "10GB for 30 days", "allowance": "10GB", "price": 5000, "validity": "30 days",
               "datacode": 5000}
            ]
            """);

    private static final JsonNode EPIN_BUNDLES = parse("""
            [
              {"amount": 50, "available": 1, "description": "₦50 Airtime"},
              {"amount": 100, "available": 1, "description": "₦100 Airtime"},
              {"amount": 200, "available": 1, "description": "₦200 Airtime"},
              {"amount": 500, "available": 1, "description": "₦500 Airtime"},
              {"amount": 1000, "available": 1, "description": "₦1000 Airtime"}
            ]
            """);

    private static final JsonNode BOUQUETS = parse("""
            [
              {"availablePricingOptions": [{"monthsPaidFor": 1, "price": 2000, "invoicePeriod": 1}],
               "code": "ACSSE36", "name": "DStv Access", "description": " "}
            ]
            """);

    private static final JsonNode ADDONS = parse("""
            [
              {"availablePricingOptions": [
                 {"monthsPaidFor": 1, "price": 5050, "invoicePeriod": 1},
                 {"monthsPaidFor": 2, "price": 10100, "invoicePeriod": 1}],
               "code": "ASIADDE36", "name": "Asian Add-on", "description": " "}
            ]
            """);

    private static final JsonNode ACCOUNT_USER = parse("""
            {"user": {
              "name": "NP NGEMA",
              "address": "6 ABIODUN ODESEYE Shomolu BU",
              "outstandingBalance": 31829,
              "district": null,
              "accountNumber": "04042404139",
              "minimumAmount": 250,
              "rawOutput": {
                "address": "6 ABIODUN ODESEYE Shomolu BU",
                "outstandingAmount": 31829,
                "name": "NP NGEMA",
                "minimumAmount": 250,
                "customerAccountType": "PRIME",
                "accountNumber": "04042404139",
                "customerDtNumber": "007903312"
              }
            }}
            """);

    private static final JsonNode MULTICHOICE_USER = parse("""
            {"user": {
              "name": "IGINLA OMOTAYO",
              "address": null,
              "outstandingBalance": 2381,
              "dueDate": "2021-11-02T00:00:00+01:00",
              "district": null,
              "accountNumber": null,
              "minimumAmount": null,
              "rawOutput": {
                "accountStatus": "OPEN",
                "firstName": "IGINLA",
                "lastName": "OMOTAYO",
                "customerType": "SUD",
                "invoicePeriod": 1,
                "dueDate": "2021-11-01T00:00:00+01:00",
                "customerNumber": 252177429
              },
              "errorMessage": null,
              "currentBouquetRaw": {
                "amount": 2565,
                "items": [
                  {"code": "FRUBE36", "price": 0, "name": "DStv FreeView Public Broadcaster E36",
                   "description": "DStv FreeView Public Broadcaster E36"},
                  {"code": "NNJ1E36", "price": 2565, "name": "DStv Yanga Bouquet E36",
                   "description": "DStv Yanga Bouquet E36"}
                ]
              },
              "currentBouquet": "DStv FreeView Public Broadcaster E36 + DStv Yanga Bouquet E36"
            }}
            """);

    private static final JsonNode JAMB_USER = parse("""
            {"user": {
              "name": "Emmanuel Amunike Michael",
              "address": null,
              "outstandingBalance": null,
              "dueDate": null,
              "district": null,
              "accountNumber": null,
              "minimumAmount": null,
              "rawOutput": {
                "firstName": "Emmanuel",
                "lastName": "Amunike",
                "gsmNo": "08000000000",
                "middleName": "Michael",
                "error": null
              },
              "errorMessage": null
            }}
            """);

    private static final JsonNode METER = parse("""
            {"name": "TESTMETER1", "address": "ABULE - EGBA BU ABULE", "outstandingBalance": "",
             "district": "ABULE - EGBA BU ABULE", "accountNumber": "04042404048"}
            """);

    private OnafriqSampleData() {
    }

    public static JsonNode balance() {
        return BALANCE;
    }

    public static JsonNode categories() {
        return CATEGORIES;
    }

    /** All services grouped by category, as in "Fetch All Billers". */
    public static JsonNode servicesByCategory() {
        ObjectNode grouped = JSON.createObjectNode();
        for (int index = 0; index < SERVICES.size(); index++) {
            Service service = SERVICES.get(index);
            JsonNode category = grouped.get(service.category());
            ArrayNode services = category == null ? grouped.putArray(service.category()) : (ArrayNode) category;
            services.add(service.toJson(index + 1));
        }
        return grouped;
    }

    /** Billers of one category, as in "Fetch Biller By Category". */
    public static JsonNode services(String category) {
        ArrayNode services = JSON.createArrayNode();
        for (int index = 0; index < SERVICES.size(); index++) {
            Service service = SERVICES.get(index);
            if (service.category().equals(category)) {
                services.add(service.toJson(index + 1));
            }
        }
        return services;
    }

    public static JsonNode airtimeProviders() {
        return AIRTIME_PROVIDERS;
    }

    public static JsonNode dataBundleProviders() {
        return DATA_BUNDLE_PROVIDERS;
    }

    public static JsonNode cableTvProviders() {
        return CABLE_TV_PROVIDERS;
    }

    public static JsonNode epinProviders() {
        return EPIN_PROVIDERS;
    }

    public static JsonNode bettingBillers() {
        return BETTING_BILLERS;
    }

    public static JsonNode electricityBillers() {
        return ELECTRICITY_BILLERS;
    }

    public static JsonNode dataBundles() {
        return DATA_BUNDLES;
    }

    public static JsonNode epinBundles() {
        return EPIN_BUNDLES;
    }

    public static JsonNode bouquets() {
        return BOUQUETS;
    }

    public static JsonNode addons() {
        return ADDONS;
    }

    /** Name finder result for the requested service: JAMB candidate, MultiChoice smartcard, or account holder. */
    public static JsonNode nameFinder(String serviceType, String accountNumber) {
        return switch (serviceType) {
            case "jamb" -> JAMB_USER;
            case "dstv", "gotv", "startimes" -> MULTICHOICE_USER;
            default -> accountUser(accountNumber);
        };
    }

    /** Betting account validation has no published example; it reuses the name finder user shape. */
    public static JsonNode bettingAccount(String accountNumber) {
        return accountUser(accountNumber);
    }

    public static JsonNode meter(String accountNumber) {
        ObjectNode meter = (ObjectNode) METER.deepCopy();
        if (accountNumber != null) {
            meter.put("accountNumber", accountNumber);
        }
        return meter;
    }

    private static JsonNode accountUser(String accountNumber) {
        ObjectNode result = (ObjectNode) ACCOUNT_USER.deepCopy();
        ObjectNode user = (ObjectNode) result.get("user");
        user.put("accountNumber", accountNumber);
        ((ObjectNode) user.get("rawOutput")).put("accountNumber", accountNumber);
        return result;
    }

    private static JsonNode parse(String json) {
        return JSON.readTree(json);
    }

    private record Service(String category, String serviceType, String name) {

        JsonNode toJson(int id) {
            ObjectNode service = JSON.createObjectNode();
            service.put("id", id);
            service.put("serviceId", serviceType.toUpperCase());
            service.put("serviceCode", "%03d".formatted(id));
            service.put("serviceCategory", category);
            service.put("serviceType", serviceType);
            service.put("serviceName", name);
            service.put("serviceDescription", "");
            service.put("serviceHandler", "CDL");
            service.put("serviceProvider", "");
            service.put("serviceEnabled", "true");
            service.put("serviceStatus", "OK");
            service.put("deployed", "true");
            service.put("created_at", "2019-12-02 09:29:31");
            service.put("updated_at", "2019-12-02 09:29:31");
            return service;
        }
    }
}
