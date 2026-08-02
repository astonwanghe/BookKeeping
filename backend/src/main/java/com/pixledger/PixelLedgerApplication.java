package com.pixledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.pixledger.mapper")
public class PixelLedgerApplication {
  public static void main(String[] args) { SpringApplication.run(PixelLedgerApplication.class, args); }
}
