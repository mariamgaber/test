package com.ejada.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.ejada.commons.*", "com.ejada.dms.*"})
public class DMS {
    public static void main(String[] args) {
        SpringApplication.run(DMS.class, args);
    }

}
