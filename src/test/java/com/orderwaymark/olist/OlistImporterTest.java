package com.orderwaymark.olist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OlistImporterTest {

    @TempDir
    Path dir;

    private void writeFixture() throws Exception {
        // Mixed quoting, like the real files.
        Files.writeString(dir.resolve(OlistImporter.ORDERS), """
                "order_id","customer_id","order_status","order_purchase_timestamp","order_approved_at","order_delivered_carrier_date","order_delivered_customer_date","order_estimated_delivery_date"
                "o2",c2,delivered,2018-01-02 10:00:00,,,,2018-01-20 00:00:00
                o1,"c1",delivered,2018-01-01 09:30:00,,,,"2018-01-15 00:00:00"
                o3,c3,delivered,2018-01-03 10:00:00,,,,2018-01-25 00:00:00
                o4,c4,delivered,2018-01-04 10:00:00,,,,2018-01-25 00:00:00
                o5,c5,canceled,2018-01-05 10:00:00,,,,
                """);
        Files.writeString(dir.resolve(OlistImporter.ORDER_ITEMS), """
                "order_id","order_item_id","product_id","seller_id","shipping_limit_date","price","freight_value"
                "o1",2,"pB",s1,2018-01-03 00:00:00,10.00,1.00
                o1,1,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o2,1,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o2,2,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o3,1,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o4,1,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o4,2,pMissing,s1,2018-01-03 00:00:00,20.00,2.00
                o5,1,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o5,2,pB,s1,2018-01-03 00:00:00,20.00,2.00
                o6,1,pA,s1,2018-01-03 00:00:00,20.00,2.00
                o6,2,pB,s1,2018-01-03 00:00:00,20.00,2.00
                o6,3,pB,s1,2018-01-03 00:00:00,20.00,2.00
                """);
        Files.writeString(dir.resolve(OlistImporter.PRODUCTS), """
                "product_id","product_category_name","product_name_lenght","product_description_lenght","product_photos_qty","product_weight_g","product_length_cm","product_height_cm","product_width_cm"
                "pA",informatica_acessorios,40,287,1,450,16,10,14
                pB,,44,276,1,1200,30,18,20
                """);
    }

    @Test
    void joinsOrdersItemsAndProductsIntoTwoItemBaskets() throws Exception {
        writeFixture();
        List<OlistBasket> baskets = OlistImporter.load(dir, 100);

        // o1: two distinct products; o2: same product twice. Excluded: o3 (1 item), o4 (unknown product),
        // o5 (no estimated delivery date), o6 (3 items, and no orders row).
        assertThat(baskets).extracting(OlistBasket::orderId).containsExactly("o1", "o2");

        OlistBasket o1 = baskets.get(0);
        assertThat(o1.purchaseDate()).isEqualTo(LocalDate.of(2018, 1, 1));
        assertThat(o1.estimatedDelivery()).isEqualTo(LocalDate.of(2018, 1, 15));
        // ordered by order_item_id, not file order
        assertThat(o1.items()).extracting(OlistBasket.Product::productId).containsExactly("pA", "pB");
        assertThat(o1.items().get(0).category()).isEqualTo("informatica_acessorios");
        assertThat(o1.items().get(0).weightGrams()).isEqualTo(450);
        assertThat(o1.items().get(1).category()).isEqualTo("unknown");

        assertThat(baskets.get(1).items()).extracting(OlistBasket.Product::productId).containsExactly("pA", "pA");
    }

    @Test
    void respectsLimit() throws Exception {
        writeFixture();
        assertThat(OlistImporter.load(dir, 1)).extracting(OlistBasket::orderId).containsExactly("o1");
    }

    @Test
    void benchmarkRunsOnFixtureAndIsDeterministic() throws Exception {
        writeFixture();
        String a = runBenchmark(dir.toString());
        String b = runBenchmark(dir.toString());
        assertThat(a).contains("2 real two-item Olist baskets").contains("SIMULATED").isEqualTo(b);
    }

    @Test
    void benchmarkReportsMissingData() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int code = OlistBenchmark.run(new String[]{dir.resolve("nothing-here").toString()}, new PrintStream(bytes));
        assertThat(code).isEqualTo(1);
        assertThat(bytes.toString()).contains("Olist CSVs not found");
    }

    @Test
    void csvParserHandlesQuotes() {
        assertThat(Csv.parseLine("a,\"b,c\",\"d\"\"e\",")).containsExactly("a", "b,c", "d\"e", "");
    }

    private static String runBenchmark(String path) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(OlistBenchmark.run(new String[]{path}, new PrintStream(bytes))).isZero();
        return bytes.toString();
    }
}
