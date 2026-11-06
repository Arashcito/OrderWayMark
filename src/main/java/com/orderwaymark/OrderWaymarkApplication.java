package com.orderwaymark;

import com.orderwaymark.olist.OlistBenchmark;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class OrderWaymarkApplication {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("benchmark")) {
            System.exit(OlistBenchmark.run(Arrays.copyOfRange(args, 1, args.length), System.out));
        }
        SpringApplication.run(OrderWaymarkApplication.class, args);
    }
}
