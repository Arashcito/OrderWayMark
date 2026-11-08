package com.orderwaymark.olist;

import com.orderwaymark.stream.Policy;
import com.orderwaymark.stream.Replay;
import com.orderwaymark.stream.StreamOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OlistStreamWorldTest {

    @TempDir
    Path dir;

    static final OlistStreamWorld.Config CFG = OlistStreamWorld.Config.defaults().withWindows(
            LocalDate.of(2017, 1, 1), LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 1), LocalDate.of(2018, 2, 1), 90);

    private void fixture() throws Exception {
        Files.writeString(dir.resolve(OlistStreamWorld.GEOLOCATION), """
                "geolocation_zip_code_prefix","geolocation_lat","geolocation_lng","geolocation_city","geolocation_state"
                "01000",-23.55,-46.63,sao paulo,SP
                01000,-23.57,-46.65,sao paulo,SP
                30000,-19.92,-43.94,belo horizonte,MG
                80000,-25.43,-49.27,curitiba,PR
                20000,-22.91,-43.17,rio de janeiro,RJ
                20000,40.0,-3.0,outlier outside brazil,RJ
                """);
        Files.writeString(dir.resolve(OlistStreamWorld.SELLERS), """
                "seller_id","seller_zip_code_prefix","seller_city","seller_state"
                s1,01000,sao paulo,SP
                s2,30000,belo horizonte,MG
                s3,80000,curitiba,PR
                """);
        Files.writeString(dir.resolve(OlistStreamWorld.CUSTOMERS), """
                "customer_id","customer_unique_id","customer_zip_code_prefix","customer_city","customer_state"
                ct1,u1,20000,rio,RJ
                ct2,u2,01000,sp,SP
                ct3,u3,30000,bh,MG
                ct4,u4,80000,cwb,PR
                ct5,u5,01000,sp,SP
                cq1,u6,01000,sp,SP
                cq2,u7,20000,rio,RJ
                cq3,u8,30000,bh,MG
                cq4,u9,01000,sp,SP
                cq5,u10,99999,nowhere,XX
                """);
        Files.writeString(dir.resolve(OlistImporter.PRODUCTS), """
                "product_id","product_category_name","product_name_lenght","product_description_lenght","product_photos_qty","product_weight_g","product_length_cm","product_height_cm","product_width_cm"
                pA,informatica,40,287,1,500,16,10,14
                pB,cama_mesa_banho,44,276,1,2000,30,18,20
                """);
        Files.writeString(dir.resolve(OlistImporter.ORDERS), """
                "order_id","customer_id","order_status","order_purchase_timestamp","order_approved_at","order_delivered_carrier_date","order_delivered_customer_date","order_estimated_delivery_date"
                t1,ct1,delivered,2017-11-01 10:00:00,,,2017-11-09 10:00:00,2017-11-25 00:00:00
                t2,ct2,delivered,2017-11-02 10:00:00,,,2017-11-12 10:00:00,2017-11-25 00:00:00
                t3,ct3,delivered,2017-11-03 10:00:00,,,2017-11-15 10:00:00,2017-11-25 00:00:00
                t4,ct4,delivered,2017-11-04 10:00:00,,,2017-11-11 10:00:00,2017-11-25 00:00:00
                t5,ct5,delivered,2017-11-05 10:00:00,,,2017-11-08 10:00:00,2017-11-25 00:00:00
                q1,cq1,delivered,2018-01-02 09:00:00,,,,2018-01-30 00:00:00
                q2,cq2,delivered,2018-01-03 09:00:00,,,,2018-01-30 00:00:00
                q3,cq3,delivered,2018-01-04 09:00:00,,,,2018-01-30 00:00:00
                q4,cq4,canceled,2018-01-05 09:00:00,,,,2018-01-30 00:00:00
                q5,cq5,delivered,2018-01-06 09:00:00,,,,2018-01-30 00:00:00
                """);
        StringBuilder items = new StringBuilder("""
                "order_id","order_item_id","product_id","seller_id","shipping_limit_date","price","freight_value"
                t1,1,pA,s1,2017-11-03 00:00:00,50.00,15.00
                t2,1,pB,s2,2017-11-03 00:00:00,80.00,25.00
                t3,1,pA,s3,2017-11-03 00:00:00,50.00,20.00
                t4,1,pB,s1,2017-11-03 00:00:00,80.00,30.00
                t5,1,pA,s1,2017-11-03 00:00:00,50.00,9.00
                q1,1,pA,s1,2018-01-03 00:00:00,50.00,9.00
                q1,2,pB,s1,2018-01-03 00:00:00,80.00,9.00
                q2,1,pA,s2,2018-01-03 00:00:00,50.00,9.00
                q4,1,pA,s1,2018-01-03 00:00:00,50.00,9.00
                q5,1,pA,s1,2018-01-03 00:00:00,50.00,9.00
                """);
        for (int i = 1; i <= 5; i++) items.append("q3,").append(i).append(",pA,s2,2018-01-03 00:00:00,50.00,9.00\n");
        Files.writeString(dir.resolve(OlistImporter.ORDER_ITEMS), items.toString());
    }

    @Test
    void joinsAllSixFilesIntoATimeOrderedStream() throws Exception {
        fixture();
        OlistStreamWorld w = OlistStreamWorld.load(dir, CFG);

        // q3 has 5 items (> maxItems 4), q4 is canceled, q5's postal code has no coordinates
        assertThat(w.orders()).extracting(StreamOrder::orderId).containsExactly("q1", "q2");
        assertThat(w.skipped()).isEqualTo(3);
        StreamOrder q1 = w.orders().get(0);
        assertThat(q1.items()).extracting(li -> li.sku()).containsExactly("informatica", "cama_mesa_banho");
        assertThat(q1.deadline()).isEqualTo(LocalDate.of(2018, 1, 30));

        assertThat(w.fits().priceRows()).isEqualTo(5);   // the five single-item training orders
        assertThat(w.fits().transitRows()).isEqualTo(5);
        assertThat(w.warehouses()).extracting(wh -> wh.id()).containsExactly("SP", "MG", "PR");
        assertThat(OlistStreamWorld.available(dir)).isTrue();
    }

    @Test
    void replaysAndBenchmarkOutputIsDeterministic() throws Exception {
        fixture();
        OlistStreamWorld w = OlistStreamWorld.load(dir, CFG);
        for (Policy p : Policy.values()) {
            var r = Replay.run(w, w.orders(), p);
            assertThat(r.orders()).isEqualTo(2);
            assertThat(r.shipped() + r.unfilled()).isEqualTo(2);
        }
        String a = bench(), b = bench();
        assertThat(a).contains("REAL:").contains("FITTED:").contains("SIMULATED:").contains("Inventory-aware").isEqualTo(b);
    }

    @Test
    void haversine() {
        // São Paulo to Rio de Janeiro is roughly 360 km in a straight line
        assertThat(OlistStreamWorld.km(-23.55, -46.63, -22.91, -43.17)).isBetween(350.0, 370.0);
    }

    @Test
    void leastSquaresRecoversALine() {
        LeastSquares ls = new LeastSquares(2);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 3; z++) ls.add(2 + 3 * x - 0.5 * z, x, z);
        double[] b = ls.solve();
        assertThat(b[0]).isCloseTo(2, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(b[1]).isCloseTo(3, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(b[2]).isCloseTo(-0.5, org.assertj.core.api.Assertions.within(1e-9));
    }

    private String bench() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(OlistStreamBenchmark.run(dir, CFG, new PrintStream(bytes))).isZero();
        return bytes.toString();
    }
}
